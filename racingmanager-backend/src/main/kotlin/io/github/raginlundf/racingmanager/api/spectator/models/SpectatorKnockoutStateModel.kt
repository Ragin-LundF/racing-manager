package io.github.raginlundf.racingmanager.api.spectator.models

import kotlinx.serialization.Serializable

@Serializable
data class SpectatorKnockoutStateModel(
    val status: String,
    val pairingMode: String,
    val rounds: List<SpectatorKnockoutRoundModel> = emptyList(),
)
