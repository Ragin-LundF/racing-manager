package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import java.util.UUID

sealed interface MeasurementGatewayEvent {
    data class HeatStarted(val heatId: UUID) : MeasurementGatewayEvent
    data class LaneFinished(
        val heatId: UUID,
        val lane: Int,
        val durationNanos: Long,
        val outcome: LaneOutcome,
    ) : MeasurementGatewayEvent
    data class HeatFinished(val heatId: UUID) : MeasurementGatewayEvent
    data class HeatTimeout(val heatId: UUID) : MeasurementGatewayEvent
    data class Error(val heatId: UUID, val message: String) : MeasurementGatewayEvent
}
