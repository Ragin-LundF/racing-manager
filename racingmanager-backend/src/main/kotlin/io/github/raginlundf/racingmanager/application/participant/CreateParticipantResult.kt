package io.github.raginlundf.racingmanager.application.participant

import io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity

sealed interface CreateParticipantResult {
    data class Success(val participant: ParticipantEntity) : CreateParticipantResult
    data object EventNotFound : CreateParticipantResult
    data object EventNotActive : CreateParticipantResult
    data class DuplicateStartNumber(val startNumber: Int) : CreateParticipantResult
}
