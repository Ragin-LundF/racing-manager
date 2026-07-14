package io.github.raginlundf.racingmanager.infrastructure.gateway

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.raginlundf.racingmanager.infrastructure.gateway.transport.WebSocketRaceDeviceTransport
import io.github.raginlundf.racingmanager.infrastructure.repositories.RaceDeviceSettingsRepository
import io.ktor.server.application.Application

private val logger = KotlinLogging.logger {}

private const val DEFAULT_ENDPOINT = "ws://raspberrypi.local:8080/race"

/**
 * Builds a [RaspberryPiMeasurementGateway] for [settings]: the in-process
 * simulator for [RaceDeviceMode.SIMULATED], or a WebSocket connection to a real
 * Raspberry Pi for [RaceDeviceMode.HARDWARE].
 */
fun buildRaceDeviceGateway(settings: RaceDeviceSettings): RaspberryPiMeasurementGateway {
    return when (settings.mode) {
        RaceDeviceMode.SIMULATED -> {
            logger.info { "Race device: simulated (in-process fake Raspberry Pi)" }
            RaspberryPiMeasurementGateway.simulated(finishTimeoutMs = settings.finishTimeoutMs)
        }

        RaceDeviceMode.HARDWARE -> {
            logger.info { "Race device: hardware at ${settings.endpoint}" }
            RaspberryPiMeasurementGateway(
                transport = WebSocketRaceDeviceTransport(endpoint = settings.endpoint),
                finishTimeoutMs = settings.finishTimeoutMs,
            )
        }
    }
}

/** Resolves the effective race-device settings — a saved UI override if present,
otherwise the `racingmanager.racedevice` startup configuration — and returns a
[ReconfigurableMeasurementGateway] so the connection can later be changed live
from the UI. Defaults to the in-process simulator so offline/dev installs work
with no hardware.
// ponytail: a single gateway per instance — MANUAL heats simply skip it, and
// the simulator serves both SIMULATED and (dev) ELECTRONIC events. Add a
// per-event Map<MeasurementType, MeasurementGateway> resolver only if one
// instance ever needs to drive real hardware and a simulator at the same time. */
fun Application.configureMeasurementGateway(
    settingsRepository: RaceDeviceSettingsRepository,
): ReconfigurableMeasurementGateway {
    val initialSettings = settingsRepository.find() ?: raceDeviceSettingsFromConfig()
    return ReconfigurableMeasurementGateway(
        initialSettings = initialSettings,
        buildDelegate = ::buildRaceDeviceGateway,
    )
}

/**
 * Startup defaults from `application.conf` / environment variables — used until
 * the settings are first saved from the UI.
 */
private fun Application.raceDeviceSettingsFromConfig(): RaceDeviceSettings {
    val config = environment.config
    val mode = RaceDeviceMode.from(
        value = config.propertyOrNull(path = "racingmanager.racedevice.mode")?.getString() ?: "simulated",
    )
    val endpoint = config.propertyOrNull(path = "racingmanager.racedevice.endpoint")?.getString() ?: DEFAULT_ENDPOINT
    val finishTimeoutMs =
        config.propertyOrNull(path = "racingmanager.racedevice.finishTimeoutMs")?.getString()?.toLong()
            ?: RaspberryPiMeasurementGateway.DEFAULT_FINISH_TIMEOUT_MS
    return RaceDeviceSettings(mode = mode, endpoint = endpoint, finishTimeoutMs = finishTimeoutMs)
}
