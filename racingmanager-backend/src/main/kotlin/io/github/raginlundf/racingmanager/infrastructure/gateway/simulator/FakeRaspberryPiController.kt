package io.github.raginlundf.racingmanager.infrastructure.gateway.simulator

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.DeviceCommand
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.DeviceErrorCode
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.DeviceEvent
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.DeviceProtocolException
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.LaneResultPayload
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.MessageCodec
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.PROTOCOL_VERSION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/**
 * In-process stand-in for the Raspberry Pi software. It speaks the same protocol
 * v1 the real controller will: it consumes command frames and emits event frames,
 * running a per-race state machine (PREPARED → RUNNING → FINISHED). On `startRace`
 * it generates a race — after [rampDelayMs] each lane finishes between [raceMinMs]
 * and [raceMaxMs], or (with [dnfProbability]) times out after [dnfTimeoutMs] and is
 * reported as a `timeout` lane in `raceFinished`.
 * One RNG stream is seeded once from [seed] and shared by every race, so repeating
 * the same race (re-arm → re-start) draws fresh numbers instead of replaying
 * identical times. Do not reseed per race: `java.util.Random`'s first `nextDouble`
 * barely moves when only the low seed bits differ, which pinned each race's DNF
 * verdict to its raceId forever.
 */
class FakeRaspberryPiController(
    private val seed: Long = DEFAULT_SEED,
    private val rampDelayMs: Long = DEFAULT_RAMP_DELAY_MS,
    private val raceMinMs: Long = DEFAULT_RACE_MIN_MS,
    private val raceMaxMs: Long = DEFAULT_RACE_MAX_MS,
    private val dnfTimeoutMs: Long = DEFAULT_DNF_TIMEOUT_MS,
    private val dnfProbability: Double = DEFAULT_DNF_PROBABILITY,
) {
    private enum class RaceState { PREPARED, RUNNING, FINISHED }

    private val scope = CoroutineScope(context = Dispatchers.Default)
    private val outgoing = MutableSharedFlow<String>(extraBufferCapacity = 64)
    private val states = ConcurrentHashMap<String, RaceState>()
    private val lanesByRace = ConcurrentHashMap<String, List<Int>>()
    private val jobs = ConcurrentHashMap<String, Job>()
    // One stream for the whole process: every race continues where the last left off,
    // so two runs of the same race can never agree. Thread-safe (CAS on AtomicLong).
    private val random = Random(seed)

    fun outgoing(): Flow<String> {
        return outgoing.asSharedFlow()
    }

    suspend fun onCommand(text: String) {
        val decoded = runCatching { MessageCodec.decodeCommand(text = text) }.getOrElse { failure ->
            logger.warn { "Dropping undecodable command: ${failure.message}" }
            (failure as? DeviceProtocolException)?.raceId?.let { raceId ->
                emit(
                    raceId = raceId,
                    event = DeviceEvent.DeviceError(
                        code = DeviceErrorCode.INVALID_STATE,
                        message = "undecodable command"
                    )
                )
            }
            return
        }
        val raceId = decoded.raceId
        when (val command = decoded.command) {
            is DeviceCommand.Hello -> emit(raceId = raceId, event = helloAck())
            is DeviceCommand.Ping -> emit(raceId = raceId, event = DeviceEvent.Pong)
            is DeviceCommand.PrepareRace -> prepare(raceId = raceId, command = command)
            is DeviceCommand.StartRace -> start(raceId = raceId)
            is DeviceCommand.AbortRace -> abort(raceId = raceId)
            is DeviceCommand.Reset -> reset(raceId = raceId)
        }
    }

    private suspend fun prepare(raceId: String?, command: DeviceCommand.PrepareRace) {
        if (raceId == null) {
            return
        }
        states[raceId] = RaceState.PREPARED
        lanesByRace[raceId] = command.lanes
        emit(raceId = raceId, event = DeviceEvent.RaceReady(lanes = command.lanes, gateState = "closed"))
    }

    private suspend fun start(raceId: String?) {
        if (raceId == null) {
            return
        }
        when (states[raceId]) {
            null -> {
                emit(
                    raceId = raceId,
                    event = DeviceEvent.DeviceError(code = DeviceErrorCode.INVALID_STATE, message = "race not prepared")
                )
                return
            }

            RaceState.RUNNING, RaceState.FINISHED -> {
                emit(
                    raceId = raceId,
                    event = DeviceEvent.DeviceError(
                        code = DeviceErrorCode.DUPLICATE_COMMAND,
                        message = "race already started"
                    )
                )
                return
            }

            RaceState.PREPARED -> Unit
        }

        val lanes = lanesByRace[raceId].orEmpty()
        states[raceId] = RaceState.RUNNING
        emit(
            raceId = raceId,
            event = DeviceEvent.RaceStarted(startedLanes = lanes, controllerMonotonicNs = System.nanoTime())
        )

        jobs[raceId] = scope.launch {
            runRace(raceId = raceId, lanes = lanes)
        }
    }

    private suspend fun runRace(raceId: String, lanes: List<Int>) {
        val plans = lanes.map { lane ->
            val dnf = random.nextDouble() < dnfProbability
            val durationMs = if (dnf) dnfTimeoutMs else raceMinMs + random.nextLong(raceMaxMs - raceMinMs + 1)
            LanePlan(lane = lane, dnf = dnf, durationMs = durationMs)
        }

        delay(timeMillis = rampDelayMs)
        if (states[raceId] != RaceState.RUNNING) {
            return
        }

        val sequence = AtomicInteger(0)
        coroutineScope {
            plans.map { plan ->
                async {
                    delay(timeMillis = plan.durationMs)
                    if (states[raceId] == RaceState.RUNNING && !plan.dnf) {
                        emit(
                            raceId = raceId,
                            event = DeviceEvent.FinishDetected(
                                lane = plan.lane,
                                finishSequence = sequence.incrementAndGet(),
                                finishMonotonicNs = System.nanoTime(),
                                elapsedNs = plan.durationMs * 1_000_000L,
                            ),
                        )
                    }
                }
            }.awaitAll()
        }

        if (states[raceId] != RaceState.RUNNING) {
            return
        }
        states[raceId] = RaceState.FINISHED
        emit(
            raceId = raceId,
            event = DeviceEvent.RaceFinished(
                results = plans.map { plan ->
                    LaneResultPayload(
                        lane = plan.lane,
                        status = if (plan.dnf) "timeout" else "finished",
                        elapsedMs = if (plan.dnf) null else plan.durationMs.toDouble(),
                    )
                },
                completionReason = "all-lanes-finished",
            ),
        )
    }

    private fun abort(raceId: String?) {
        raceId ?: return
        jobs.remove(raceId)?.cancel()
        states.remove(raceId)
        lanesByRace.remove(raceId)
    }

    private fun reset(raceId: String?) {
        if (raceId == null) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            states.clear()
            lanesByRace.clear()
        } else {
            abort(raceId = raceId)
        }
    }

    private fun helloAck(): DeviceEvent.HelloAck {
        return DeviceEvent.HelloAck(
            deviceId = "fake-raspberry-pi",
            firmwareVersion = "sim-1.0",
            protocolVersion = PROTOCOL_VERSION,
            capabilities = listOf("shared-gate", "two-lane"),
        )
    }

    private suspend fun emit(raceId: String?, event: DeviceEvent) {
        outgoing.emit(value = MessageCodec.encodeEvent(raceId = raceId, event = event))
    }

    private data class LanePlan(val lane: Int, val dnf: Boolean, val durationMs: Long)

    companion object {
        const val DEFAULT_SEED = 42L
        const val DEFAULT_RAMP_DELAY_MS = 3_000L
        const val DEFAULT_RACE_MIN_MS = 4_000L
        const val DEFAULT_RACE_MAX_MS = 7_000L
        const val DEFAULT_DNF_TIMEOUT_MS = 10_000L
        const val DEFAULT_DNF_PROBABILITY = 0.10
    }
}
