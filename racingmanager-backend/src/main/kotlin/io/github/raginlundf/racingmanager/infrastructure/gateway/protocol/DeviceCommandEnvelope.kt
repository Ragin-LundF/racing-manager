package io.github.raginlundf.racingmanager.infrastructure.gateway.protocol

import kotlinx.serialization.Serializable

/** Protocol v1 envelope wrapping a command payload (raspberry.md §2). */
@Serializable
data class DeviceCommandEnvelope(
    val messageId: String,
    val timestamp: String,
    val payload: DeviceCommand,
    val raceId: String? = null,
    val protocolVersion: Int = PROTOCOL_VERSION,
)
