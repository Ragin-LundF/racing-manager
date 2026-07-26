package io.github.raginlundf.racingmanager.api.event.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateEventRequestModel(
    val name: String,
    val description: String? = null,
    val laneType: String = "TWO_LANE",
    val measurementType: String = "SIMULATED",
    val maxParticipants: Int? = null,
    val trackLength: Int? = null,
    val expectedVersion: Long,
)
