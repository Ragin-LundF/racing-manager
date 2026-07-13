package io.github.raginlundf.racingmanager.application.heat

sealed interface GatewayCancelResult {
    data object Success : GatewayCancelResult
    data class Error(val message: String) : GatewayCancelResult
}
