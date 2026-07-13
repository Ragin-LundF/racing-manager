package io.github.raginlundf.racingmanager.api.spectator.models

import kotlinx.serialization.Serializable

@Serializable
data class SpectatorKnockoutRoundModel(
    val roundNumber: Int,
    val matches: List<SpectatorKnockoutMatchModel> = emptyList(),
)
