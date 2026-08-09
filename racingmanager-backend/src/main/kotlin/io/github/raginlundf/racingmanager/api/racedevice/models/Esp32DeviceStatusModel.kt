package io.github.raginlundf.racingmanager.api.racedevice.models

import kotlinx.serialization.Serializable

/** One expected ESP32 module's live status, for the settings UI's device-status
    panel — the closest equivalent to the other modes' connection test, since
    there is nothing to dial out to in an inbound-only mode. */
@Serializable
data class Esp32DeviceStatusModel(
    val deviceId: String,
    val connected: Boolean,
    val online: Boolean,
    val lane: Int? = null,
    val role: String? = null,
    val lastHeartbeatAt: String? = null,
)
