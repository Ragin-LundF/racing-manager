package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.event.MeasurementType
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import kotlinx.coroutines.flow.Flow

/** Abstraction over the race timing device (the spec's `RaceDevice`). A real
    Raspberry Pi adapter and an in-process simulator implement the same contract;
    [io.github.raginlundf.racingmanager.application.heat.HeatService] depends only
    on this. Timing arrives asynchronously via [events]. */
interface MeasurementGateway {
    /** Prepare the device for a heat (spec `prepareRace`): reset/close the gate,
        enable the lanes, check sensors — without releasing the cars. */
    suspend fun arm(heat: HeatEntity): GatewayArmResult

    /** Release the shared gate and begin timing (spec `startRace`). Called once a
        heat transitions to STARTED. */
    suspend fun start(heat: HeatEntity)

    /** Abort a heat and return the device to a safe state (spec `abortRace`).
        Device reset on (re)connect is handled internally by the transport.
        // ponytail: no explicit reset() on the contract yet — add one only if a
        // deployment ever needs to force a full device reset independent of a heat. */
    suspend fun cancel(heatId: java.util.UUID): GatewayCancelResult

    fun events(): Flow<MeasurementGatewayEvent>

    /** The gateway that must drive a heat of an event timed by [measurementType].
        A concrete adapter speaks for exactly one device and returns itself; only
        [io.github.raginlundf.racingmanager.infrastructure.gateway.ReconfigurableMeasurementGateway]
        overrides this, so a SIMULATED event runs on the in-process simulator even
        while real hardware is configured. */
    fun forMeasurementType(measurementType: MeasurementType): MeasurementGateway {
        return this
    }
}
