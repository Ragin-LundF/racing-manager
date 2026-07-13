package io.github.raginlundf.racingmanager.application.participant

import io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity

sealed interface ParticipantActionResult {
    data class Success(val participant: ParticipantEntity) : ParticipantActionResult
    data object NotFound : ParticipantActionResult
    data object AlreadyInactive : ParticipantActionResult
    data object AlreadyActive : ParticipantActionResult
}
