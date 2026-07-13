package io.github.raginlundf.racingmanager.api.knockout.models

import kotlinx.serialization.Serializable

@Serializable
data class KnockoutMatchResponseModel(
    val id: String,
    val tournamentId: String,
    val roundNumber: Int,
    val matchNumber: Int,
    val participant1Id: String? = null,
    val participant2Id: String? = null,
    val winnerId: String? = null,
    val heatId: String? = null,
    val status: String,
    val createdAt: String,
)
