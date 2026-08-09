package io.github.raginlundf.racingmanager.api.racedevice.models

import kotlinx.serialization.Serializable

/** ESP32 direct-connect options; only read when the mode is `ESP32_WEBSOCKET_DIRECT`.
    `useRaceControlHandshake`/`useTimeSync` are surfaced so the UI can show them,
    but must currently be saved as `false` — see
    [io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.Esp32WebSocketDirectMeasurementGateway]. */
@Serializable
data class Esp32SettingsModel(
    val expectedDeviceIds: List<String>,
    val registerTimeoutMs: Long,
    val useRaceControlHandshake: Boolean,
    val useTimeSync: Boolean,
    val useDeviceHeartbeat: Boolean,
    val heartbeatTimeoutMs: Long,
    val armTimeoutMs: Long,
    val timeSyncRounds: Int,
    val rawLogPath: String,
)
