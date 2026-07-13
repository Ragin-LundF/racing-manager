package io.github.raginlundf.racingmanager.api

import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceMode
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceSettings
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaspberryPiMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.ReconfigurableMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.repositories.RaceDeviceSettingsRepository

/** Throwaway race-device wiring for route tests that must satisfy
    [configureRouting]'s signature but do not exercise the race-device routes.
    Always the in-process simulator — no socket, no hardware. */
fun testRaceDeviceGateway(): ReconfigurableMeasurementGateway {
    return ReconfigurableMeasurementGateway(
        initialSettings = RaceDeviceSettings(mode = RaceDeviceMode.SIMULATED, endpoint = "ws://test", finishTimeoutMs = 30_000),
        buildDelegate = { RaspberryPiMeasurementGateway.simulated() },
    )
}

fun testRaceDeviceSettingsRepository(): RaceDeviceSettingsRepository {
    return RaceDeviceSettingsRepository()
}
