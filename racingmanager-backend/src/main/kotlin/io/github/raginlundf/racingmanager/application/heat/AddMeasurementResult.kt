package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus

sealed interface AddMeasurementResult {
    data class Success(val heat: HeatEntity) : AddMeasurementResult
    data object NotFound : AddMeasurementResult
    data class InvalidStatus(val current: HeatStatus) : AddMeasurementResult
}
