package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.raginlundf.racingmanager.application.heat.CloseableMeasurementGateway
import io.github.raginlundf.racingmanager.application.heat.GatewayArmResult
import io.github.raginlundf.racingmanager.application.heat.GatewayCancelResult
import io.github.raginlundf.racingmanager.application.heat.MeasurementGatewayEvent
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

private const val MILLIS_MAX = 0xFFFFFFFFL
private const val NANOS_PER_MILLI = 1_000_000L

/** A drop below this many milliseconds of board uptime is read as a reset rather
    than as the ~49.7-day `millis()` rollover. */
private const val ROLLOVER_MARGIN_MS = 60_000L

private fun command(lane: TwoLaneLane, state: DeviceState): String {
    return "CMD;$lane;$state"
}

/** [io.github.raginlundf.racingmanager.application.heat.MeasurementGateway] for the
    Arduino two-lane light barrier described in `.plan/Adruino-impl.md`.

    The device has **no start trigger and no gate**: each lane has its own start and
    finish photo diode, and a lane's time comes from the board alone — by default the
    elapsed value the board reports on `FINISH`. The host clock never contributes to a
    measured time; it only drives the DNF deadline, the false-start window and the log
    (§4.3).

    Contract mapping: [arm] locks the lanes and requires the ready banner, [start]
    arms them (that is the release the operator triggers) and begins the per-lane
    deadline, [cancel] locks them again. Unknown states are logged and never
    interpreted. A finish with no measured time is rejected as the §6.1 sensor fault;
    the [ArduinoTwoLaneSettings.falseStartWindowMs] check is off by default because
    this board legitimately reports `START` a few ms after `ARM`.

    // ponytail: one heat at a time — the board holds a single state per lane and
    // cannot run two heats in parallel, so a second arm() is rejected rather than
    // queued. */
class TwoLaneSerialMeasurementGateway(
    private val port: SerialLine,
    private val config: ArduinoTwoLaneSettings,
    private val laneTimeoutMs: Long,
    private val rawLog: RawTimingLog,
    private val scope: CoroutineScope = CoroutineScope(context = Dispatchers.Default),
) : CloseableMeasurementGateway {
    private val events = MutableSharedFlow<MeasurementGatewayEvent>(extraBufferCapacity = 64)
    private val ready = MutableStateFlow(value = false)
    private val bannerLanes = mutableSetOf<TwoLaneLane>()
    private val stateLock = Mutex()

    private var liveHeat: LiveHeat? = null

    @Volatile
    private var connectionError: String? = null

    init {
        scope.launch { consumeDevice() }
    }

    override fun events(): Flow<MeasurementGatewayEvent> {
        return events.asSharedFlow()
    }

    override suspend fun arm(heat: HeatEntity): GatewayArmResult {
        connectionError?.let { return GatewayArmResult.Error(message = it) }
        val runs = mapLanes(heat = heat)
            ?: return GatewayArmResult.Error(
                message = "The timing device has two lanes; heat ${heat.heatNumber} has ${heat.lanes.size}",
            )
        val banner = withTimeoutOrNull(timeMillis = config.readyTimeoutMs) { ready.first { it } }
        if (banner == null) {
            return GatewayArmResult.Error(
                message = "No ready banner from the timing device within ${config.readyTimeoutMs} ms",
            )
        }
        return stateLock.withLock { armLocked(heat = heat, runs = runs) }
    }

    override suspend fun start(heat: HeatEntity) {
        stateLock.withLock {
            val live = liveHeat
            if (live == null || live.heatId != heat.id) {
                logger.error { "start() for heat ${heat.id} but that heat is not armed on the device" }
                return@withLock
            }
            val failure = runCatching {
                live.runs.forEach { (deviceLane, run) ->
                    send(command = command(lane = deviceLane, state = DeviceState.ARM))
                    run.armedAtHost = Clock.System.now()
                }
            }.exceptionOrNull()
            if (failure != null) {
                failHeatLocked(live = live, message = "Cannot arm the lanes: ${failure.message}")
                return@withLock
            }
            live.started = true
            events.emit(value = MeasurementGatewayEvent.HeatStarted(heatId = live.heatId))
            live.runs.values.forEach { run -> run.deadlineJob = launchDeadline(heatId = live.heatId, run = run) }
        }
    }

    override suspend fun cancel(heatId: UUID): GatewayCancelResult {
        return stateLock.withLock {
            val live = liveHeat
            if (live == null || live.heatId != heatId) {
                return@withLock GatewayCancelResult.Error(message = "Heat $heatId is not armed on the device")
            }
            clearLocked(live = live)
            val locked = runCatching {
                live.runs.keys.forEach { send(command = command(lane = it, state = DeviceState.LOCK)) }
            }
            locked.fold(
                onSuccess = { GatewayCancelResult.Success },
                onFailure = { GatewayCancelResult.Error(message = it.message ?: "Cannot lock the lanes") },
            )
        }
    }

    override suspend fun close() {
        scope.cancel()
        port.close()
    }

    /** Sends a command verbatim. The protocol was reverse engineered and several
        commands are undocumented (§5.1), so this channel exists to resolve them
        against real hardware without a code change. */
    internal suspend fun sendRaw(command: String) {
        send(command = command)
    }

    private suspend fun consumeDevice() {
        val opened = runCatching { port.open() }
        if (opened.isFailure) {
            val message = opened.exceptionOrNull()?.message ?: "Cannot open the serial port"
            connectionError = message
            logger.error { "Timing device unavailable: $message" }
            return
        }
        port.lines().collect { line -> onLine(line = line) }
        // The flow completes when the board disconnects: every open measurement is
        // invalid from here on (§6.2).
        connectionError = "The timing device disconnected"
        ready.value = false
        stateLock.withLock {
            bannerLanes.clear()
            liveHeat?.let { failHeatLocked(live = it, message = "The timing device disconnected during the heat") }
        }
    }

    private suspend fun onLine(line: String) {
        rawLog.received(line = line)
        val event = TwoLaneLineParser.parse(line = line, hostTimestamp = Clock.System.now()) ?: return
        stateLock.withLock {
            when (event.state) {
                DeviceState.LOCK -> onLock(event = event)
                DeviceState.START -> onStart(event = event)
                DeviceState.FINISH -> onFinish(event = event)
                // ARM only acknowledges our command; RUN and UNKNOWN are open items
                // in the spec and must leave the race state untouched.
                DeviceState.ARM -> Unit
                DeviceState.RUN, DeviceState.UNKNOWN -> logger.info {
                    "Ignoring ${event.state} on lane ${event.lane}: '$line'"
                }
            }
        }
    }

    /** LOCK is only a readiness signal. It deliberately says nothing about a reset:
        the board also sends it in reply to our own `CMD;<lane>;LOCK` and after every
        FINISH, so treating it as a fault would kill heats that are running fine. A
        reset is recognised from the board's uptime dropping instead (§4.3.3). */
    private fun onLock(event: TwoLaneEvent) {
        bannerLanes += event.lane
        if (bannerLanes.containsAll(TwoLaneLane.entries)) {
            ready.value = true
        }
    }

    private suspend fun onStart(event: TwoLaneEvent) {
        val run = activeRun(event = event) ?: return
        val live = liveHeat ?: return
        val armedAt = run.armedAtHost
        if (armedAt == null) {
            logger.warn { "Discarding START on lane ${run.lane}: the lane was not armed yet ('${event.raw}')" }
            return
        }
        if (detectBoardReset(live = live, value = event.value)) return
        val msAfterArm = (event.hostTimestamp - armedAt).inWholeMilliseconds
        if (msAfterArm < config.falseStartWindowMs) {
            failHeatLocked(
                live = live,
                message = "Lane ${run.lane}: START $msAfterArm ms after ARM — false start or sensor fault " +
                    "(threshold ${config.falseStartWindowMs} ms)",
            )
            return
        }
        if (run.startMillis != null) {
            logger.warn { "Ignoring repeated START on lane ${run.lane}, keeping the first ('${event.raw}')" }
            return
        }
        run.startMillis = event.value
    }

    private suspend fun onFinish(event: TwoLaneEvent) {
        val run = activeRun(event = event) ?: return
        val live = liveHeat ?: return
        val startMillis = run.startMillis
        if (startMillis == null) {
            logger.warn { "Discarding FINISH on lane ${run.lane}: no START was recorded ('${event.raw}')" }
            return
        }
        // ELAPSED is what the observed board does; TIMESTAMP stays available because
        // the protocol was reverse engineered and another build may differ.
        val durationMs = when (config.finishSemantics) {
            FinishSemantics.TIMESTAMP -> {
                if (detectBoardReset(live = live, value = event.value)) return
                (event.value - startMillis) and MILLIS_MAX
            }

            FinishSemantics.ELAPSED -> event.value
        }
        // The §6.1 sensor fault, precisely: the board reports a finish with no time
        // (`<lane>;FINISH;0`). No physical pass takes zero milliseconds.
        if (durationMs <= 0) {
            failHeatLocked(
                live = live,
                message = "Lane ${run.lane}: FINISH with no measured time ('${event.raw}') — sensor fault. " +
                    "Check that the emitter LEDs are powered and aligned.",
            )
            return
        }
        settleLocked(
            live = live,
            run = run,
            event = MeasurementGatewayEvent.LaneFinished(
                heatId = live.heatId,
                lane = run.lane,
                durationNanos = durationMs * NANOS_PER_MILLI,
                outcome = LaneOutcome.FINISHED,
            ),
        )
    }

    /** The run this event belongs to, or null when there is nothing to apply it to. */
    private fun activeRun(event: TwoLaneEvent): LaneRun? {
        val live = liveHeat
        if (live == null || !live.started) {
            logger.debug { "No started heat on the device — discarding '${event.raw}'" }
            return null
        }
        val run = live.runs[event.lane]
        if (run == null) {
            logger.warn { "Lane ${event.lane} is not part of heat ${live.heatId} — discarding '${event.raw}'" }
            return null
        }
        return if (run.settled) null else run
    }

    /** True when [value] shows the board restarted, in which case the heat is failed:
        timestamps are only valid within one board session (§4.3.3). A drop right at
        the top of the 32-bit range is the documented rollover, not a reset. */
    private suspend fun detectBoardReset(live: LiveHeat, value: Long): Boolean {
        val highest = live.highestBoardMillis
        val rolledOver = highest > MILLIS_MAX - ROLLOVER_MARGIN_MS && value < ROLLOVER_MARGIN_MS
        if (value >= highest || rolledOver) {
            live.highestBoardMillis = value
            return false
        }
        failHeatLocked(
            live = live,
            message = "Board uptime dropped from $highest ms to $value ms during the heat — the board reset, " +
                "all open measurements are void",
        )
        return true
    }

    private suspend fun armLocked(heat: HeatEntity, runs: Map<TwoLaneLane, LaneRun>): GatewayArmResult {
        liveHeat?.let {
            return GatewayArmResult.Error(message = "Heat ${it.heatId} is still running on the timing device")
        }
        return runCatching { runs.keys.forEach { send(command = command(lane = it, state = DeviceState.LOCK)) } }.fold(
            onSuccess = {
                liveHeat = LiveHeat(heatId = heat.id, runs = runs)
                GatewayArmResult.Success
            },
            onFailure = { GatewayArmResult.Error(message = it.message ?: "Cannot lock the lanes") },
        )
    }

    /** Maps heat lanes onto the device's A/B in ascending order; null when the heat
        does not fit the two-lane hardware. */
    private fun mapLanes(heat: HeatEntity): Map<TwoLaneLane, LaneRun>? {
        val lanes = heat.lanes.map { it.lane }.sorted()
        if (lanes.isEmpty() || lanes.size > TwoLaneLane.entries.size) return null
        return lanes.mapIndexed { index, lane -> TwoLaneLane.entries[index] to LaneRun(lane = lane) }.toMap()
    }

    private fun launchDeadline(heatId: UUID, run: LaneRun): Job {
        return scope.launch {
            delay(timeMillis = laneTimeoutMs)
            stateLock.withLock {
                val live = liveHeat
                if (live == null || live.heatId != heatId || run.settled) return@withLock
                run.timedOut = true
                logger.warn { "Lane ${run.lane} did not finish within $laneTimeoutMs ms — DNF" }
                settleLocked(
                    live = live,
                    run = run,
                    event = MeasurementGatewayEvent.LaneFinished(
                        heatId = heatId,
                        lane = run.lane,
                        durationNanos = 0L,
                        outcome = LaneOutcome.DNF,
                    ),
                )
            }
        }
    }

    private suspend fun settleLocked(live: LiveHeat, run: LaneRun, event: MeasurementGatewayEvent) {
        run.settled = true
        run.deadlineJob?.cancel()
        events.emit(value = event)
        if (live.runs.values.any { !it.settled }) return
        val timedOut = live.runs.values.any { it.timedOut }
        clearLocked(live = live)
        events.emit(
            value = if (timedOut) {
                MeasurementGatewayEvent.HeatTimeout(heatId = live.heatId)
            } else {
                MeasurementGatewayEvent.HeatFinished(heatId = live.heatId)
            },
        )
    }

    /** Reports a fault and drops the heat: no lane result is written, so a stuck
        sensor or a board reset never produces a time in the database. */
    private suspend fun failHeatLocked(live: LiveHeat, message: String) {
        clearLocked(live = live)
        logger.error { "Heat ${live.heatId} failed on the timing device: $message" }
        events.emit(value = MeasurementGatewayEvent.Error(heatId = live.heatId, message = message))
    }

    private fun clearLocked(live: LiveHeat) {
        live.runs.values.forEach { it.deadlineJob?.cancel() }
        liveHeat = null
    }

    private suspend fun send(command: String) {
        rawLog.sent(line = command)
        port.write(line = command)
    }

    private class LaneRun(val lane: Int) {
        var armedAtHost: Instant? = null
        var startMillis: Long? = null
        var settled = false
        var timedOut = false
        var deadlineJob: Job? = null
    }

    private class LiveHeat(val heatId: UUID, val runs: Map<TwoLaneLane, LaneRun>) {
        var started = false

        /** Highest board `millis()` seen in THIS heat. Deliberately per-heat: the
            board may reset between heats (observed once in a real session) and that
            must not void the next heat — only a drop while a heat is open does. */
        var highestBoardMillis = 0L
    }
}
