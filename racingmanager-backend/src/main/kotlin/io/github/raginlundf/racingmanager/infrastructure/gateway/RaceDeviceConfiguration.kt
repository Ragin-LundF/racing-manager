package io.github.raginlundf.racingmanager.infrastructure.gateway

import io.github.raginlundf.racingmanager.application.heat.MeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.transport.WebSocketRaceDeviceTransport
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application

private val logger = KotlinLogging.logger {}

/** Builds the [MeasurementGateway] from `racingmanager.racedevice` config. Defaults
    to the in-process simulator so offline/dev installs work with no hardware.
    // ponytail: a single gateway per instance — MANUAL heats simply skip it, and the
    // simulator serves both SIMULATED and (dev) ELECTRONIC events. Add a per-event
    // Map<MeasurementType, MeasurementGateway> resolver only if one instance ever
    // needs to drive real hardware and a simulator at the same time. */
fun Application.configureMeasurementGateway(): MeasurementGateway {
    val config = environment.config
    val mode = RaceDeviceMode.from(
        value = config.propertyOrNull(path = "racingmanager.racedevice.mode")?.getString() ?: "simulated",
    )
    val finishTimeoutMs = config.propertyOrNull(path = "racingmanager.racedevice.finishTimeoutMs")?.getString()?.toLong()
        ?: RaspberryPiMeasurementGateway.DEFAULT_FINISH_TIMEOUT_MS

    return when (mode) {
        RaceDeviceMode.SIMULATED -> {
            logger.info { "Race device: simulated (in-process fake Raspberry Pi)" }
            RaspberryPiMeasurementGateway.simulated(finishTimeoutMs = finishTimeoutMs)
        }
        RaceDeviceMode.HARDWARE -> {
            val endpoint = config.property(path = "racingmanager.racedevice.endpoint").getString()
            logger.info { "Race device: hardware at $endpoint" }
            RaspberryPiMeasurementGateway(
                transport = WebSocketRaceDeviceTransport(endpoint = endpoint),
                finishTimeoutMs = finishTimeoutMs,
            )
        }
    }
}
