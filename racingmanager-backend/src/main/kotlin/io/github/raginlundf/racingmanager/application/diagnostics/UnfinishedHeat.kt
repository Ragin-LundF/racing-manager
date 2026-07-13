package io.github.raginlundf.racingmanager.application.diagnostics

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity

data class UnfinishedHeat(
    val heat: HeatEntity,
    val event: EventEntity,
)
