package io.github.raginlundf.racingmanager.application.heat

/** A [MeasurementGateway] that owns a device connection and must be torn down when
    the race-device settings change. Implemented by every concrete adapter so
    [io.github.raginlundf.racingmanager.infrastructure.gateway.ReconfigurableMeasurementGateway]
    can swap between them without knowing which hardware is behind it. */
interface CloseableMeasurementGateway : MeasurementGateway {
    /** Stops consuming device events and releases the underlying connection. */
    suspend fun close()
}
