package io.github.raginlundf.racingmanager.api.bootstrap.models

import kotlinx.serialization.Serializable

@Serializable
data class PackagedEvent(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String,
    val laneType: String,
    val measurementType: String,
    val maxParticipants: Int? = null,
    val trackLength: Int? = null,
    val participants: List<PackagedParticipant> = emptyList(),
)
