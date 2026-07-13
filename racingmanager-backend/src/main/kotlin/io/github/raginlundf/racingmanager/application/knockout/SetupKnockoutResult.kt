package io.github.raginlundf.racingmanager.application.knockout

import io.github.raginlundf.racingmanager.domain.knockout.KnockoutTournamentEntity

sealed interface SetupKnockoutResult {
    data class Success(val tournament: KnockoutTournamentEntity) : SetupKnockoutResult
    data class AlreadyExists(val tournament: KnockoutTournamentEntity) : SetupKnockoutResult
    data object EventNotFound : SetupKnockoutResult
    data object EventNotActive : SetupKnockoutResult
    data object QualificationNotFinalized : SetupKnockoutResult
    data object NotEnoughParticipants : SetupKnockoutResult
}
