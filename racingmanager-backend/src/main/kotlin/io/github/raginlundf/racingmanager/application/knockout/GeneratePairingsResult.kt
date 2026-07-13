package io.github.raginlundf.racingmanager.application.knockout

import io.github.raginlundf.racingmanager.domain.knockout.KnockoutStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutTournamentEntity

sealed interface GeneratePairingsResult {
    data class Success(val tournament: KnockoutTournamentEntity) : GeneratePairingsResult
    data object TournamentNotFound : GeneratePairingsResult
    data class InvalidStatus(val status: KnockoutStatus) : GeneratePairingsResult
    data object PairingsAlreadyExist : GeneratePairingsResult
    data object NotEnoughParticipants : GeneratePairingsResult
}
