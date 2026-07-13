package io.github.raginlundf.racingmanager.application.knockout

sealed interface RecordMatchResult {
    data object Success : RecordMatchResult
    data object TournamentNotFound : RecordMatchResult
    data object MatchNotFound : RecordMatchResult
    data object MatchAlreadyCompleted : RecordMatchResult
    data object WinnerNotInMatch : RecordMatchResult
}
