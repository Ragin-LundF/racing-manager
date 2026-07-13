package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus

sealed interface FinishHeatResult {
    data class Success(val heat: HeatEntity) : FinishHeatResult
    data object NotFound : FinishHeatResult
    data class InvalidStatus(val current: HeatStatus) : FinishHeatResult
}
