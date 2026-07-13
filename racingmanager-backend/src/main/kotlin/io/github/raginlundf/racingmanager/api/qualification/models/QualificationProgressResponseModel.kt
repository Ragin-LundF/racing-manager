package io.github.raginlundf.racingmanager.api.qualification.models

import kotlinx.serialization.Serializable

@Serializable
data class QualificationProgressResponseModel(
    val status: String,
    val totalHeats: Int,
    val completedHeats: Int,
    val inProgressHeats: Int,
    val plannedHeats: Int,
    val cancelledHeats: Int,
    val totalParticipants: Int,
    val participantsWithResults: Int,
)
