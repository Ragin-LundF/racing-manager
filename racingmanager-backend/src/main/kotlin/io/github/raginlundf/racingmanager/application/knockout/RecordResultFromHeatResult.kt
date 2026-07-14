package io.github.raginlundf.racingmanager.application.knockout

sealed interface RecordResultFromHeatResult {
    data object Success : RecordResultFromHeatResult

    /** The heat is not tied to a knockout match that still needs a result. */
    data object NoMatch : RecordResultFromHeatResult

    /** No lane finished (e.g. both timed out), so no winner could be derived — re-race the heat. */
    data object NoWinner : RecordResultFromHeatResult
}
