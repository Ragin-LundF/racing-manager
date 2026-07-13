package io.github.raginlundf.racingmanager.application.results

import io.github.raginlundf.racingmanager.application.knockout.KnockoutResultEntry
import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking

data class EventResultSnapshot(
    val event: EventEntity,
    val qualificationRankings: List<QualificationRanking>,
    val knockoutResults: List<KnockoutResultEntry>,
    val allHeats: List<HeatEntity>,
    val isSimulated: Boolean,
)
