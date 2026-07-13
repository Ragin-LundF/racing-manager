package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity

sealed interface CreateHeatResult {
    data class Success(val heat: HeatEntity) : CreateHeatResult
    data object EventNotFound : CreateHeatResult
    data object EventNotActive : CreateHeatResult
    data object ParticipantNotFound : CreateHeatResult
    data object ParticipantNotActive : CreateHeatResult
}
