package io.github.raginlundf.racingmanager.application.knockout

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutTournamentEntity
import java.util.UUID

sealed interface SetupKnockoutResult {
    data class Success(val tournament: KnockoutTournamentEntity) : SetupKnockoutResult
    data class AlreadyExists(val tournament: KnockoutTournamentEntity) : SetupKnockoutResult
    data object EventNotFound : SetupKnockoutResult
    data object EventNotActive : SetupKnockoutResult
    data object QualificationNotFinalized : SetupKnockoutResult
    data object NotEnoughParticipants : SetupKnockoutResult
}

sealed interface GeneratePairingsResult {
    data class Success(val tournament: KnockoutTournamentEntity) : GeneratePairingsResult
    data object TournamentNotFound : GeneratePairingsResult
    data class InvalidStatus(val status: KnockoutStatus) : GeneratePairingsResult
    data object PairingsAlreadyExist : GeneratePairingsResult
    data object NotEnoughParticipants : GeneratePairingsResult
}

sealed interface CreateHeatForMatchResult {
    data class Success(val heat: HeatEntity) : CreateHeatForMatchResult
    data object TournamentNotFound : CreateHeatForMatchResult
    data object MatchNotFound : CreateHeatForMatchResult
    data object MatchAlreadyCompleted : CreateHeatForMatchResult
    data object MissingParticipants : CreateHeatForMatchResult
}

sealed interface RecordMatchResult {
    data object Success : RecordMatchResult
    data object TournamentNotFound : RecordMatchResult
    data object MatchNotFound : RecordMatchResult
    data object MatchAlreadyCompleted : RecordMatchResult
    data object WinnerNotInMatch : RecordMatchResult
}

sealed interface FinalizeKnockoutResult {
    data object Success : FinalizeKnockoutResult
    data object TournamentNotFound : FinalizeKnockoutResult
    data class InvalidStatus(val status: KnockoutStatus) : FinalizeKnockoutResult
    data class IncompleteMatches(val count: Int) : FinalizeKnockoutResult
}

data class KnockoutResultEntry(
    val rank: Int,
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    val startNumber: Int,
    val club: String? = null,
)
