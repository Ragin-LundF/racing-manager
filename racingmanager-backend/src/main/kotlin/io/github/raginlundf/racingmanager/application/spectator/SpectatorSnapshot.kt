package io.github.raginlundf.racingmanager.application.spectator

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking

data class SpectatorSnapshot(
    val event: EventEntity,
    val currentHeat: HeatEntity?,
    val upcomingHeats: List<HeatEntity>,
    val qualificationRankings: List<QualificationRanking>,
    val qualificationStatus: String?,
    val knockout: SpectatorKnockoutState?,
)
