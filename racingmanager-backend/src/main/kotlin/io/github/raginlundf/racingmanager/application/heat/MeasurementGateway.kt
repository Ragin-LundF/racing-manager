package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import kotlinx.coroutines.flow.Flow

interface MeasurementGateway {
    suspend fun arm(heat: HeatEntity): GatewayArmResult
    suspend fun cancel(heatId: java.util.UUID): GatewayCancelResult
    fun events(): Flow<MeasurementGatewayEvent>

    /** Called once a heat transitions to STARTED. Gateways backed by real
        hardware (manual/electronic timing) have nothing to do here — timing
        arrives asynchronously via [events]. Only a simulated gateway needs
        to actually generate a race. */
    fun simulateHeat(heat: HeatEntity) {}
}
