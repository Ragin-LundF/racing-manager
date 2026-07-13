package io.github.raginlundf.racingmanager.infrastructure.gateway.protocol

/** A decoded command frame: the envelope meta plus the typed [command]. */
data class DecodedCommand(
    val messageId: String,
    val raceId: String?,
    val command: DeviceCommand,
)
