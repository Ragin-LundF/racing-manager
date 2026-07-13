package io.github.raginlundf.racingmanager.application.participant

import io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity

sealed interface ImportResult {
    data class Completed(
        val created: List<ParticipantEntity>,
        val errors: List<ImportRowError>,
    ) : ImportResult
    data object EventNotFound : ImportResult
    data object EventNotActive : ImportResult
}
