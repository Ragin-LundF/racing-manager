package io.github.raginlundf.racingmanager.api.spectator.models

import kotlinx.serialization.Serializable

@Serializable
data class SpectatorHeatModel(
    val id: String,
    val heatNumber: Int,
    val round: Int,
    val status: String,
    val lanes: List<SpectatorLaneModel>,
    val hasResult: Boolean = false,
)
