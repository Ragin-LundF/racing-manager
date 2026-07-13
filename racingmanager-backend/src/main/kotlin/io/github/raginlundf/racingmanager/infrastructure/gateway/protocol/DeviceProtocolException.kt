package io.github.raginlundf.racingmanager.infrastructure.gateway.protocol

/** Raised when a frame cannot be understood: unsupported [PROTOCOL_VERSION] or an
    undecodable message type. [raceId] is carried when the envelope was readable so
    the failure can be attributed to a heat. */
class DeviceProtocolException(
    val raceId: String?,
    message: String,
) : RuntimeException(message)
