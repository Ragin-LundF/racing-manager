package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.domain.heat.Measurement
import kotlinx.coroutines.flow.Flow

interface MeasurementGateway {
    suspend fun arm(heat: HeatEntity): GatewayArmResult
    suspend fun cancel(heatId: java.util.UUID): GatewayCancelResult
    fun events(): Flow<MeasurementGatewayEvent>
}

sealed interface GatewayArmResult {
    data object Success : GatewayArmResult
    data class Error(val message: String) : GatewayArmResult
}

sealed interface GatewayCancelResult {
    data object Success : GatewayCancelResult
    data class Error(val message: String) : GatewayCancelResult
}

sealed interface MeasurementGatewayEvent {
    data class HeatStarted(val heatId: java.util.UUID) : MeasurementGatewayEvent
    data class LaneFinished(
        val heatId: java.util.UUID,
        val lane: Int,
        val durationNanos: Long,
        val outcome: LaneOutcome,
    ) : MeasurementGatewayEvent
    data class HeatFinished(val heatId: java.util.UUID) : MeasurementGatewayEvent
    data class HeatTimeout(val heatId: java.util.UUID) : MeasurementGatewayEvent
    data class Error(val heatId: java.util.UUID, val message: String) : MeasurementGatewayEvent
}
