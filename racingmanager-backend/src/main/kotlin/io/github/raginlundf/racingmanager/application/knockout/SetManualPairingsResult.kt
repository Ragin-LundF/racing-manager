package io.github.raginlundf.racingmanager.application.knockout

import io.github.raginlundf.racingmanager.domain.knockout.KnockoutStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutTournamentEntity

sealed interface SetManualPairingsResult {
    data class Success(val tournament: KnockoutTournamentEntity) : SetManualPairingsResult
    data object TournamentNotFound : SetManualPairingsResult
    data class InvalidStatus(val status: KnockoutStatus) : SetManualPairingsResult
    data object PairingsAlreadyExist : SetManualPairingsResult
    data object NotEnoughParticipants : SetManualPairingsResult
    data object WrongPairingMode : SetManualPairingsResult
}
