package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import java.util.UUID

sealed interface HeatServiceEvent {
    data class HeatCreated(val heat: HeatEntity) : HeatServiceEvent
    data class HeatStateChanged(val heat: HeatEntity) : HeatServiceEvent
    data class HeatResultAccepted(val heatId: UUID) : HeatServiceEvent
    data class HeatResultRejected(val heatId: UUID) : HeatServiceEvent
}
