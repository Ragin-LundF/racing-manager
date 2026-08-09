package io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct

import kotlin.time.Instant

/** A point-in-time view of one expected ESP32 module, for the settings UI's
    device-status panel — there is nothing to "test-dial" in an inbound-only
    mode, so this is the closest equivalent to the other modes' connection test. */
data class Esp32DeviceSnapshot(
    val deviceId: String,
    val connected: Boolean,
    /** [connected] and, when heartbeat tracking is enabled, recently heard from. */
    val online: Boolean,
    val lane: Int?,
    val role: Esp32ModuleRole?,
    val lastHeartbeatAt: Instant?,
)
