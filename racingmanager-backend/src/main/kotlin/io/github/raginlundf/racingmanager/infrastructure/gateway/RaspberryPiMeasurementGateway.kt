package io.github.raginlundf.racingmanager.infrastructure.gateway

import io.github.raginlundf.racingmanager.application.heat.CloseableMeasurementGateway
import io.github.raginlundf.racingmanager.application.heat.GatewayArmResult
import io.github.raginlundf.racingmanager.application.heat.GatewayCancelResult
import io.github.raginlundf.racingmanager.application.heat.MeasurementGateway
import io.github.raginlundf.racingmanager.application.heat.MeasurementGatewayEvent
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.DeviceCommand
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.DeviceEvent
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.MessageCodec
import io.github.raginlundf.racingmanager.infrastructure.gateway.simulator.FakeRaspberryPiController
import io.github.raginlundf.racingmanager.infrastructure.gateway.transport.LoopbackRaceDeviceTransport
import io.github.raginlundf.racingmanager.infrastructure.gateway.transport.RaceDeviceTransport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.UUID

private val logger = KotlinLogging.logger {}

/** Adapter translating the [MeasurementGateway] contract onto the race-device
    protocol: `arm`→prepareRace, `start`→startRace, `cancel`→abortRace. Device
    events are decoded, de-duplicated by messageId, and mapped back to
    [MeasurementGatewayEvent]s. The [transport] decides whether it talks to a real
    Raspberry Pi (WebSocket) or an in-process [FakeRaspberryPiController] (loopback). */
class RaspberryPiMeasurementGateway(
    private val transport: RaceDeviceTransport,
    private val finishTimeoutMs: Long = DEFAULT_FINISH_TIMEOUT_MS,
    private val scope: CoroutineScope = CoroutineScope(context = Dispatchers.Default),
) : CloseableMeasurementGateway {
    private val events = MutableSharedFlow<MeasurementGatewayEvent>(extraBufferCapacity = 64)

    // ponytail: a plain set of seen ids — a race day is at most a few thousand
    // frames, so unbounded growth is a non-issue. Swap for a bounded LRU if this
    // ever runs long-lived against chatty hardware.
    // ponytail: process-lifetime dedup. If a real device reuses messageIds across a heat repeat within
    // one process, its frames would be dropped here — revisit (scope the set per heat run) only if
    // observed on real hardware; the simulator emits fresh ids so repeats work today.
    private val seenMessageIds = Collections.synchronizedSet(mutableSetOf<String>())

    init {
        scope.launch {
            transport.connect()
            transport.incoming().collect { frame -> handleFrame(frame = frame) }
        }
    }

    override fun events(): Flow<MeasurementGatewayEvent> {
        return events.asSharedFlow()
    }

    /** Tears the gateway down: stops consuming device frames and closes the
        transport (including its reconnect loop). Called when the
        [ReconfigurableMeasurementGateway] swaps to new settings so an old
        connection does not linger and keep reconnecting. */
    override suspend fun close() {
        scope.cancel()
        transport.close()
    }

    override suspend fun arm(heat: HeatEntity): GatewayArmResult {
        val command = DeviceCommand.PrepareRace(
            lanes = heat.lanes.map { it.lane },
            finishTimeoutMs = finishTimeoutMs,
        )
        return runCatching { send(raceId = heat.id.toString(), command = command) }
            .fold(
                onSuccess = { GatewayArmResult.Success },
                onFailure = { GatewayArmResult.Error(message = it.message ?: "prepareRace failed") },
            )
    }

    override suspend fun start(heat: HeatEntity) {
        runCatching { send(raceId = heat.id.toString(), command = DeviceCommand.StartRace) }
            .onFailure { logger.error(throwable = it) { "startRace failed for heat ${heat.id}" } }
    }

    override suspend fun cancel(heatId: UUID): GatewayCancelResult {
        return runCatching { send(raceId = heatId.toString(), command = DeviceCommand.AbortRace) }
            .fold(
                onSuccess = { GatewayCancelResult.Success },
                onFailure = { GatewayCancelResult.Error(message = it.message ?: "abortRace failed") },
            )
    }

    private suspend fun send(raceId: String, command: DeviceCommand) {
        transport.send(text = MessageCodec.encodeCommand(raceId = raceId, command = command))
    }

    private suspend fun handleFrame(frame: String) {
        val decoded = runCatching { MessageCodec.decodeEvent(text = frame) }.getOrElse { failure ->
            logger.warn { "Dropping undecodable event frame: ${failure.message}" }
            return
        }
        if (!seenMessageIds.add(element = decoded.messageId)) {
            return
        }
        val heatId = decoded.raceId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return

        when (val event = decoded.event) {
            is DeviceEvent.FinishDetected -> emit(
                event = MeasurementGatewayEvent.LaneFinished(
                    heatId = heatId,
                    lane = event.lane,
                    durationNanos = event.elapsedNs,
                    outcome = LaneOutcome.FINISHED,
                ),
            )
            is DeviceEvent.RaceFinished -> handleRaceFinished(heatId = heatId, event = event)
            is DeviceEvent.DeviceError -> emit(
                event = MeasurementGatewayEvent.Error(heatId = heatId, message = "${event.code}: ${event.message}"),
            )
            // helloAck / raceReady / raceStarted / pong carry no domain result:
            // HeatService owns the authoritative started/finished timestamps.
            else -> Unit
        }
    }

    private suspend fun handleRaceFinished(heatId: UUID, event: DeviceEvent.RaceFinished) {
        // Lanes that never crossed the sensor produced no finishDetected — emit
        // their non-finished outcome now so every lane yields exactly one result.
        event.results.forEach { result ->
            val outcome = nonFinishedOutcome(status = result.status) ?: return@forEach
            emit(
                event = MeasurementGatewayEvent.LaneFinished(
                    heatId = heatId,
                    lane = result.lane,
                    durationNanos = 0L,
                    outcome = outcome,
                ),
            )
        }
        if (event.completionReason == "timeout") {
            emit(event = MeasurementGatewayEvent.HeatTimeout(heatId = heatId))
        } else {
            emit(event = MeasurementGatewayEvent.HeatFinished(heatId = heatId))
        }
    }

    private fun nonFinishedOutcome(status: String): LaneOutcome? {
        return when (status) {
            "timeout" -> LaneOutcome.DNF
            "not-started" -> LaneOutcome.DNS
            "invalid" -> LaneOutcome.DSQ
            else -> null // "finished" already emitted via finishDetected; "aborted" ignored
        }
    }

    private suspend fun emit(event: MeasurementGatewayEvent) {
        events.emit(value = event)
    }

    companion object {
        const val DEFAULT_FINISH_TIMEOUT_MS = 30_000L

        /** Wires the adapter to an in-process [FakeRaspberryPiController] over a
            loopback transport — the "simulated" race device. */
        fun simulated(
            seed: Long = FakeRaspberryPiController.DEFAULT_SEED,
            rampDelayMs: Long = FakeRaspberryPiController.DEFAULT_RAMP_DELAY_MS,
            raceMinMs: Long = FakeRaspberryPiController.DEFAULT_RACE_MIN_MS,
            raceMaxMs: Long = FakeRaspberryPiController.DEFAULT_RACE_MAX_MS,
            dnfTimeoutMs: Long = FakeRaspberryPiController.DEFAULT_DNF_TIMEOUT_MS,
            dnfProbability: Double = FakeRaspberryPiController.DEFAULT_DNF_PROBABILITY,
            finishTimeoutMs: Long = DEFAULT_FINISH_TIMEOUT_MS,
        ): RaspberryPiMeasurementGateway {
            val controller = FakeRaspberryPiController(
                seed = seed,
                rampDelayMs = rampDelayMs,
                raceMinMs = raceMinMs,
                raceMaxMs = raceMaxMs,
                dnfTimeoutMs = dnfTimeoutMs,
                dnfProbability = dnfProbability,
            )
            return RaspberryPiMeasurementGateway(
                transport = LoopbackRaceDeviceTransport(controller = controller),
                finishTimeoutMs = finishTimeoutMs,
            )
        }
    }
}
