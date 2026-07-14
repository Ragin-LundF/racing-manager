package io.github.raginlundf.racingmanager.application.knockout

sealed interface ResetMatchResult {
    data object Success : ResetMatchResult

    /** The heat is not tied to a completed knockout match — nothing to undo. */
    data object NoMatch : ResetMatchResult

    /** The match's winner already advanced into a completed downstream match; unwinding is unsafe. */
    data object HasCompletedDependent : ResetMatchResult
}
