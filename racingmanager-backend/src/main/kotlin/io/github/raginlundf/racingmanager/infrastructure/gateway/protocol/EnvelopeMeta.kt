package io.github.raginlundf.racingmanager.infrastructure.gateway.protocol

import kotlinx.serialization.Serializable

/** Envelope meta decoded on its own (ignoring the payload) so a frame that fails
    full decoding — unknown payload type — can still be attributed to a [raceId]. */
@Serializable
data class EnvelopeMeta(
    val raceId: String? = null,
    val protocolVersion: Int = PROTOCOL_VERSION,
)
