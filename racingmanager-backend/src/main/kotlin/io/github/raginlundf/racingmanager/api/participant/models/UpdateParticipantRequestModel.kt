package io.github.raginlundf.racingmanager.api.participant.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateParticipantRequestModel(
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
)
