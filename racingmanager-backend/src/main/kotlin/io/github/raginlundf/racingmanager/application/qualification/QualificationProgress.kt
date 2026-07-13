package io.github.raginlundf.racingmanager.application.qualification

import io.github.raginlundf.racingmanager.domain.qualification.QualificationStatus

data class QualificationProgress(
    val status: QualificationStatus,
    val totalHeats: Int,
    val completedHeats: Int,
    val inProgressHeats: Int,
    val plannedHeats: Int,
    val cancelledHeats: Int,
    val totalParticipants: Int,
    val participantsWithResults: Int,
)
