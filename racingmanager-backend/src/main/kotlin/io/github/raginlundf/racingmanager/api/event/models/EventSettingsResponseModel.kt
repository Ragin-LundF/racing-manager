package io.github.raginlundf.racingmanager.api.event.models

import kotlinx.serialization.Serializable

@Serializable
data class EventSettingsResponseModel(
    val laneType: String,
    val measurementType: String,
    val maxParticipants: Int? = null,
)
