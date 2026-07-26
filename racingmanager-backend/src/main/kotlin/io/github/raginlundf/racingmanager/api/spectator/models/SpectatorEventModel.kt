package io.github.raginlundf.racingmanager.api.spectator.models

import kotlinx.serialization.Serializable

@Serializable
data class SpectatorEventModel(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String,
    val laneType: String,
    val measurementType: String,
    /** Course length in meters, or null. The spectator view derives km/h from it. */
    val trackLength: Int? = null,
)
