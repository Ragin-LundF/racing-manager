package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus

sealed interface ArmHeatResult {
    data class Success(val heat: HeatEntity) : ArmHeatResult
    data object NotFound : ArmHeatResult
    data class InvalidStatus(val current: HeatStatus) : ArmHeatResult
    data class GatewayError(val message: String) : ArmHeatResult
}
