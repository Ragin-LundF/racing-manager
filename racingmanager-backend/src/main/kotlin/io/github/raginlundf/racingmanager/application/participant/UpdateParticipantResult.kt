package io.github.raginlundf.racingmanager.application.participant

import io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity

sealed interface UpdateParticipantResult {
    data class Success(val participant: ParticipantEntity) : UpdateParticipantResult
    data object NotFound : UpdateParticipantResult
    data class DuplicateStartNumber(val startNumber: Int) : UpdateParticipantResult
}
