package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity

sealed interface RepeatHeatResult {
    data class Success(val heat: HeatEntity) : RepeatHeatResult
    data object NotFound : RepeatHeatResult
}
