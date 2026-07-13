package io.github.raginlundf.racingmanager.application.knockout

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity

sealed interface CreateHeatForMatchResult {
    data class Success(val heat: HeatEntity) : CreateHeatForMatchResult
    data object TournamentNotFound : CreateHeatForMatchResult
    data object MatchNotFound : CreateHeatForMatchResult
    data object MatchAlreadyCompleted : CreateHeatForMatchResult
    data object MissingParticipants : CreateHeatForMatchResult
}
