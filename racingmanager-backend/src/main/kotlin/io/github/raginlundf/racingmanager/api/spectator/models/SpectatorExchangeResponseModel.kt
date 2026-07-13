package io.github.raginlundf.racingmanager.api.spectator.models

import kotlinx.serialization.Serializable

@Serializable
data class SpectatorExchangeResponseModel(
    val accessToken: String,
    val expiresIn: Long,
    val eventId: String,
)
