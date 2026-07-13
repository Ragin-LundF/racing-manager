package io.github.raginlundf.racingmanager.application.knockout

import io.github.raginlundf.racingmanager.domain.knockout.KnockoutStatus

sealed interface FinalizeKnockoutResult {
    data object Success : FinalizeKnockoutResult
    data object TournamentNotFound : FinalizeKnockoutResult
    data class InvalidStatus(val status: KnockoutStatus) : FinalizeKnockoutResult
    data class IncompleteMatches(val count: Int) : FinalizeKnockoutResult
}
