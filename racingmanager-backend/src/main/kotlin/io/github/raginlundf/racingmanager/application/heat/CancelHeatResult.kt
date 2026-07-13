package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus

sealed interface CancelHeatResult {
    data class Success(val heat: HeatEntity) : CancelHeatResult
    data object NotFound : CancelHeatResult
    data class InvalidStatus(val current: HeatStatus) : CancelHeatResult
}
