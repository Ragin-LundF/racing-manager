package io.github.raginlundf.racingmanager.api.event.models

import kotlinx.serialization.Serializable

@Serializable
data class EventResponseModel(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String,
    val settings: EventSettingsResponseModel,
    val version: Long,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String? = null,
    val activatedAt: String? = null,
)

@Serializable
data class EventSettingsResponseModel(
    val laneType: String,
    val measurementType: String,
    val maxParticipants: Int? = null,
)
