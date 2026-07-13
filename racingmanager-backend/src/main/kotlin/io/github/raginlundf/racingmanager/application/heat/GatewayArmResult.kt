package io.github.raginlundf.racingmanager.application.heat

sealed interface GatewayArmResult {
    data object Success : GatewayArmResult
    data class Error(val message: String) : GatewayArmResult
}
