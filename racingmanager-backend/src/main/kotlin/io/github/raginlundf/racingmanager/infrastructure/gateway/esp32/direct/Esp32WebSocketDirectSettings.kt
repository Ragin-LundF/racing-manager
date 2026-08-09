package io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct

import kotlinx.serialization.Serializable

/** Connection and protocol-profile settings for the ESP32 WebSocket Direct
    Connect mode. Persisted as one JSON column on the race-device settings row,
    so serialization is part of the contract — same pattern as
    [io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.ArduinoTwoLaneSettings].

    The concrete deployment times a lane from its start module's beam break to
    its finish module's beam break; nothing needs to arm, release, or clock-sync
    the devices for that. [useRaceControlHandshake] and [useTimeSync] exist for a
    future deployment that does (a start gate, RS485 fallback, multi-Pi clock
    alignment) and default to off. */
@Serializable
data class Esp32WebSocketDirectSettings(
    val expectedDeviceIds: List<String> = DEFAULT_DEVICE_IDS,
    /** How long to wait for `device.register` after a socket connects. */
    val registerTimeoutMs: Long = DEFAULT_REGISTER_TIMEOUT_MS,

    /** When false (the concrete-case default): `arm()`/`start()`/`cancel()` never
        send `race.arm`/`race.start`/`race.reset` and never wait for `race.armed` —
        they only open/close the gateway's local listening window for a heat's
        lanes. When true, the full handshake from `PROTOCOL.md` runs. */
    val useRaceControlHandshake: Boolean = false,
    /** When false (the concrete-case default): a lane's duration is the gateway's
        own receipt-time delta between the start and finish `sensor.event`
        frames — accurate to WiFi latency jitter, not device-synchronized. When
        true, the `time.sync_request`/`response` exchange runs and durations use
        each device's `sync_timestamp_us`. */
    val useTimeSync: Boolean = false,
    /** Cheap online/offline status for the settings UI; harmless if the firmware
        never sends `device.heartbeat`, since a missing heartbeat only affects the
        status display, not timing. */
    val useDeviceHeartbeat: Boolean = true,

    /** Only relevant when [useDeviceHeartbeat]: offline after this many ms without
        a heartbeat (~5 missed 1 s heartbeats, per `PROTOCOL.md`). */
    val heartbeatTimeoutMs: Long = DEFAULT_HEARTBEAT_TIMEOUT_MS,
    /** Only relevant when [useRaceControlHandshake]: how long to wait for `race.armed`
        from every required device before
        failing [io.github.raginlundf.racingmanager.application.heat.GatewayArmResult]. */
    val armTimeoutMs: Long = DEFAULT_ARM_TIMEOUT_MS,
    /** Only relevant when [useTimeSync]: `PROTOCOL.md` requires "at least five rounds". */
    val timeSyncRounds: Int = DEFAULT_TIME_SYNC_ROUNDS,

    val rawLogPath: String = DEFAULT_RAW_LOG_PATH,
) {
    companion object {
        val DEFAULT_DEVICE_IDS = listOf("lane-1-start", "lane-1-finish", "lane-2-start", "lane-2-finish")
        const val DEFAULT_REGISTER_TIMEOUT_MS = 10_000L
        const val DEFAULT_HEARTBEAT_TIMEOUT_MS = 5_000L
        const val DEFAULT_ARM_TIMEOUT_MS = 5_000L
        const val DEFAULT_TIME_SYNC_ROUNDS = 5
        const val DEFAULT_RAW_LOG_PATH = "raw-esp32-timing.log"
    }
}
