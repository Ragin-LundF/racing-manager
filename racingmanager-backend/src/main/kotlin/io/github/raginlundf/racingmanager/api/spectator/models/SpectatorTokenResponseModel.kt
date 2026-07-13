package io.github.raginlundf.racingmanager.api.spectator.models

import kotlinx.serialization.Serializable

@Serializable
data class SpectatorTokenResponseModel(
    val exchangeCode: String,
    val expiresIn: Long,
)
