package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.heat.HeatStatus

sealed interface RejectResult {
    data object Success : RejectResult
    data object NotFound : RejectResult
    data class InvalidStatus(val current: HeatStatus) : RejectResult
}
