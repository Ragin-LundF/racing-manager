package io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.raginlundf.racingmanager.application.heat.CloseableMeasurementGateway
import io.github.raginlundf.racingmanager.application.heat.GatewayArmResult
import io.github.raginlundf.racingmanager.application.heat.GatewayCancelResult
import io.github.raginlundf.racingmanager.application.heat.MeasurementGatewayEvent
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.RawTimingLog
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.protocol.Esp32Message
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.protocol.Esp32MessageCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

private const val MAX_LANES = 2
private const val BEAM_BROKEN = "beam_broken"

/** [io.github.raginlundf.racingmanager.application.heat.MeasurementGateway] for up
    to 4 ESP32 light-barrier modules dialing into this backend's own WebSocket
    server (`docs/track_setup/en/PROTOCOL.md`). One instance tracks every connected
    module; [handleConnection] is called once per inbound socket by the hardware
    WebSocket route.

    The concrete deployment times a lane from its start module's beam break to its
    finish module's beam break: no gate, no device-side arming. [arm]/[start]/[cancel]
    only open/close the gateway's own "listening window" for a heat's lanes — nothing
    is sent to the devices, which just keep reporting `sensor.event` regardless.
    Duration is the gateway's own receipt-time delta between the two events, which is
    accurate to WiFi latency jitter (single-digit ms typically), not device-synchronized.

    `Esp32WebSocketDirectSettings.useRaceControlHandshake`/`useTimeSync` gate
    protocol features (`race.arm/armed/start/reset`, `time.sync_*`) this gateway does
    not implement yet — see `docs/track_setup/en/PROTOCOL.md` for the full contract.
    // ponytail: rather than half-wire those messages, the constructor simply refuses
    // to start with either flag on. Implement the send/wait logic and flip the guard
    // once a deployment actually needs a start gate or cross-device clock sync. */
class Esp32WebSocketDirectMeasurementGateway(
    private val settings: Esp32WebSocketDirectSettings,
    private val laneTimeoutMs: Long,
    private val rawLog: RawTimingLog,
    private val scope: CoroutineScope = CoroutineScope(context = Dispatchers.Default),
) : CloseableMeasurementGateway {
    init {
        require(!settings.useRaceControlHandshake) {
            "ESP32 race-control handshake is not implemented yet; keep useRaceControlHandshake=false"
        }
        require(!settings.useTimeSync) {
            "ESP32 time-sync is not implemented yet; keep useTimeSync=false"
        }
    }

    private val events = MutableSharedFlow<MeasurementGatewayEvent>(extraBufferCapacity = 64)
    private val stateLock = Mutex()
    private val devices = ConcurrentHashMap<String, DeviceConnection>()

    private var liveHeat: LiveHeat? = null

    override fun events(): Flow<MeasurementGatewayEvent> {
        return events.asSharedFlow()
    }

    override suspend fun arm(heat: HeatEntity): GatewayArmResult {
        val runs = mapLanes(heat = heat)
            ?: return GatewayArmResult.Error(
                message = "The ESP32 direct-connect hardware has $MAX_LANES lanes; " +
                    "heat ${heat.heatNumber} has ${heat.lanes.size}",
            )
        val missing = runs.keys
            .flatMap { lane ->
                listOf(
                    deviceIdFor(lane = lane, role = Esp32ModuleRole.START),
                    deviceIdFor(lane = lane, role = Esp32ModuleRole.FINISH),
                )
            }
            .filterNot { devices.containsKey(it) }
        if (missing.isNotEmpty()) {
            return GatewayArmResult.Error(message = "Not connected: ${missing.joinToString()}")
        }
        return stateLock.withLock {
            val current = liveHeat
            if (current != null) {
                return@withLock GatewayArmResult.Error(
                    message = "Heat ${current.heatId} is still running on the ESP32 hardware",
                )
            }
            liveHeat = LiveHeat(heatId = heat.id, runs = runs)
            GatewayArmResult.Success
        }
    }

    override suspend fun start(heat: HeatEntity) {
        stateLock.withLock {
            val live = liveHeat
            if (live == null || live.heatId != heat.id) {
                logger.error { "start() for heat ${heat.id} but that heat is not armed on the ESP32 hardware" }
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
                return@withLock GatewayCancelResult.Error(message = "Heat $heatId is not armed on the ESP32 hardware")
            }
            clearLocked(live = live)
            GatewayCancelResult.Success
        }
    }

    override suspend fun close() {
        scope.cancel()
        devices.values.toList().forEach { connection ->
            runCatching { connection.session.close(reason = "Race device closing") }
        }
        devices.clear()
    }

    /** A point-in-time snapshot of every expected module, connected or not, for the
        settings UI's device-status panel. */
    fun deviceSnapshots(): List<Esp32DeviceSnapshot> {
        val now = Clock.System.now()
        return settings.expectedDeviceIds.map { deviceId ->
            val connection = devices[deviceId]
            val heartbeatStale = connection != null &&
                settings.useDeviceHeartbeat &&
                now - connection.lastHeartbeatAt > settings.heartbeatTimeoutMs.milliseconds
            val heartbeatFresh = connection != null && !heartbeatStale
            Esp32DeviceSnapshot(
                deviceId = deviceId,
                connected = connection != null,
                online = heartbeatFresh,
                lane = connection?.lane,
                role = connection?.role,
                lastHeartbeatAt = connection?.lastHeartbeatAt,
            )
        }
    }

    /** Owns one inbound socket end to end: waits for `device.register`, then
        dispatches every subsequent frame until the device disconnects. Called by
        the hardware WebSocket route once per accepted connection. */
    suspend fun handleConnection(session: Esp32DeviceSession) {
        var connection: DeviceConnection? = null
        val registerDeadline = scope.launch {
            delay(timeMillis = settings.registerTimeoutMs)
            if (connection == null) {
                logger.warn { "Closing ESP32 socket: no device.register within ${settings.registerTimeoutMs} ms" }
                session.close(reason = "Expected device.register within ${settings.registerTimeoutMs} ms")
            }
        }

        session.incoming().collect { text ->
            val receivedAt = Clock.System.now()
            rawLog.received(line = text)
            val message = Esp32MessageCodec.decodeOrNull(text = text)
            val established = connection
            when {
                established == null -> {
                    connection = registerConnection(session = session, message = message)
                    registerDeadline.cancel()
                }
                message == null -> logger.warn { "Discarding undecodable frame from ${established.deviceId}" }
                else -> dispatch(connection = established, message = message, receivedAt = receivedAt)
            }
        }

        registerDeadline.cancel()
        connection?.let { onDisconnected(connection = it) }
    }

    private suspend fun registerConnection(session: Esp32DeviceSession, message: Esp32Message?): DeviceConnection? {
        val register = message as? Esp32Message.DeviceRegister
        if (register == null) {
            logger.warn { "Rejecting ESP32 connection: expected device.register as the first message, got $message" }
            session.close(reason = "Expected device.register as the first message")
            return null
        }
        val identity = Esp32DeviceIdentity.parse(deviceId = register.deviceId)
        if (identity == null || register.deviceId !in settings.expectedDeviceIds) {
            logger.warn { "Rejecting unknown ESP32 device_id '${register.deviceId}'" }
            session.close(reason = "Unknown device_id")
            return null
        }
        val connection = DeviceConnection(
            deviceId = register.deviceId,
            lane = identity.lane,
            role = identity.role,
            bootId = register.bootId,
            session = session,
        )
        devices[connection.deviceId] = connection
        logger.info { "ESP32 module ${connection.deviceId} registered (lane ${identity.lane}, role ${identity.role})" }
        return connection
    }

    private suspend fun dispatch(connection: DeviceConnection, message: Esp32Message, receivedAt: Instant) {
        when (message) {
            is Esp32Message.SensorEvent -> onSensorEvent(
                connection = connection,
                event = message,
                receivedAt = receivedAt,
            )

            is Esp32Message.DeviceHeartbeat -> onHeartbeat(connection = connection, heartbeat = message)
            is Esp32Message.DeviceRegister -> logger.debug {
                "Ignoring repeated device.register from ${connection.deviceId}"
            }

            else -> logger.debug {
                "Ignoring ${message::class.simpleName} from ${connection.deviceId}: not used by this deployment"
            }
        }
    }

    private fun onHeartbeat(connection: DeviceConnection, heartbeat: Esp32Message.DeviceHeartbeat) {
        connection.lastHeartbeatAt = Clock.System.now()
        connection.sensors = heartbeat.sensors
    }

    private suspend fun onSensorEvent(
        connection: DeviceConnection,
        event: Esp32Message.SensorEvent,
        receivedAt: Instant,
    ) {
        if (event.deviceId != connection.deviceId || event.bootId != connection.bootId) {
            logger.warn { "Ignoring sensor.event with mismatched device_id/boot_id from ${connection.deviceId}" }
            return
        }
        if (event.sequence <= connection.lastSequence) {
            logger.debug { "Ignoring duplicate sensor.event sequence ${event.sequence} from ${connection.deviceId}" }
            return
        }
        connection.lastSequence = event.sequence
        if (event.lane != connection.lane || event.role != connection.role.wireValue) {
            logger.warn {
                "sensor.event lane/role mismatch from ${connection.deviceId}: got lane=${event.lane} role=${event.role}"
            }
            return
        }
        if (event.event != BEAM_BROKEN) {
            logger.debug {
                "Ignoring sensor.event '${event.event}' from ${connection.deviceId}: only $BEAM_BROKEN is handled"
            }
            return
        }

        stateLock.withLock {
            val live = liveHeat ?: return@withLock
            if (!live.started) return@withLock
            val run = live.runs[event.lane] ?: return@withLock
            if (run.settled) return@withLock
            when (connection.role) {
                Esp32ModuleRole.START -> onLaneStart(run = run, at = receivedAt)
                Esp32ModuleRole.FINISH -> onLaneFinish(live = live, run = run, at = receivedAt)
            }
        }
    }

    private fun onLaneStart(run: LaneRun, at: Instant) {
        if (run.startAt != null) {
            logger.debug { "Ignoring repeated start beam-break on lane ${run.lane}, keeping the first" }
            return
        }
        run.startAt = at
    }

    private suspend fun onLaneFinish(live: LiveHeat, run: LaneRun, at: Instant) {
        val startAt = run.startAt
        if (startAt == null) {
            logger.warn { "Discarding finish beam-break on lane ${run.lane}: no start was recorded" }
            return
        }
        val durationNanos = (at - startAt).inWholeNanoseconds
        if (durationNanos <= 0) {
            failHeatLocked(
                live = live,
                message = "Lane ${run.lane}: finish at or before start — sensor fault or clock skew",
            )
            return
        }
        settleLocked(
            live = live,
            run = run,
            event = MeasurementGatewayEvent.LaneFinished(
                heatId = live.heatId,
                lane = run.lane,
                durationNanos = durationNanos,
                outcome = LaneOutcome.FINISHED,
            ),
        )
    }

    private suspend fun onDisconnected(connection: DeviceConnection) {
        devices.remove(connection.deviceId, connection)
        logger.info { "ESP32 module ${connection.deviceId} disconnected" }
        stateLock.withLock {
            val live = liveHeat ?: return@withLock
            if (live.runs.containsKey(connection.lane)) {
                failHeatLocked(
                    live = live,
                    message = "Device ${connection.deviceId} disconnected during heat ${live.heatId}",
                )
            }
        }
    }

    private fun mapLanes(heat: HeatEntity): Map<Int, LaneRun>? {
        val lanes = heat.lanes.map { it.lane }.sorted()
        if (lanes.isEmpty() || lanes.size > MAX_LANES) return null
        return lanes.associateWith { lane -> LaneRun(lane = lane) }
    }

    private fun deviceIdFor(lane: Int, role: Esp32ModuleRole): String {
        return "lane-$lane-${role.wireValue}"
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

    private suspend fun failHeatLocked(live: LiveHeat, message: String) {
        clearLocked(live = live)
        logger.error { "Heat ${live.heatId} failed on the ESP32 hardware: $message" }
        events.emit(value = MeasurementGatewayEvent.Error(heatId = live.heatId, message = message))
    }

    private fun clearLocked(live: LiveHeat) {
        live.runs.values.forEach { it.deadlineJob?.cancel() }
        liveHeat = null
    }

    private class DeviceConnection(
        val deviceId: String,
        val lane: Int,
        val role: Esp32ModuleRole,
        val bootId: String,
        val session: Esp32DeviceSession,
    ) {
        @Volatile var lastHeartbeatAt: Instant = Clock.System.now()

        @Volatile var lastSequence: Long = -1L

        @Volatile var sensors: Map<String, String> = emptyMap()
    }

    private class LaneRun(val lane: Int) {
        var startAt: Instant? = null
        var settled = false
        var timedOut = false
        var deadlineJob: Job? = null
    }

    private class LiveHeat(val heatId: UUID, val runs: Map<Int, LaneRun>) {
        var started = false
    }
}
