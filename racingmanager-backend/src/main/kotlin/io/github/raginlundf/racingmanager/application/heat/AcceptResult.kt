package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.heat.HeatStatus

sealed interface AcceptResult {
    data object Success : AcceptResult
    data object NotFound : AcceptResult
    data class InvalidStatus(val current: HeatStatus) : AcceptResult
}
