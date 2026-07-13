package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus

sealed interface StartHeatResult {
    data class Success(val heat: HeatEntity) : StartHeatResult
    data object NotFound : StartHeatResult
    data class InvalidStatus(val current: HeatStatus) : StartHeatResult
}
