package io.github.raginlundf.racingmanager.api.participant.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateParticipantRequestModel(
    val startNumber: Int? = null,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
    val vehicleName: String? = null,
    val vehicleCategory: String? = null,
)
