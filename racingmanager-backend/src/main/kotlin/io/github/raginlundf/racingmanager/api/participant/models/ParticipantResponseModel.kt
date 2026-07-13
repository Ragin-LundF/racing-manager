package io.github.raginlundf.racingmanager.api.participant.models

import kotlinx.serialization.Serializable

@Serializable
data class ParticipantResponseModel(
    val id: String,
    val eventId: String,
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
    val status: String,
    val sortOrder: Int? = null,
    val vehicle: VehicleResponseModel? = null,
    val createdAt: String,
    val updatedAt: String? = null,
)
