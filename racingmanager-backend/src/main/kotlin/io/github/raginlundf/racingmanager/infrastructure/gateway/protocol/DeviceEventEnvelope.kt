package io.github.raginlundf.racingmanager.infrastructure.gateway.protocol

import kotlinx.serialization.Serializable

/** Protocol v1 envelope wrapping an event payload (raspberry.md §2). */
@Serializable
data class DeviceEventEnvelope(
    val messageId: String,
    val timestamp: String,
    val payload: DeviceEvent,
    val raceId: String? = null,
    val protocolVersion: Int = PROTOCOL_VERSION,
)
