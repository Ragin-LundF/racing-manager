package io.github.raginlundf.racingmanager.api.spectator.models

import kotlinx.serialization.Serializable

@Serializable
data class SpectatorKnockoutMatchModel(
    val id: String,
    val roundNumber: Int,
    val matchNumber: Int,
    val participant1Id: String? = null,
    val participant2Id: String? = null,
    val winnerId: String? = null,
    val status: String,
    val isBye: Boolean = false,
)
