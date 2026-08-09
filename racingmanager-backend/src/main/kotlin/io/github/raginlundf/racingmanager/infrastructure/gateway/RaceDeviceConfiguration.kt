package io.github.raginlundf.racingmanager.infrastructure.gateway

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.raginlundf.racingmanager.application.heat.CloseableMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.ArduinoTwoLaneSettings
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.FinishSemantics
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.JSerialCommLine
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.RawTimingLog
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.TwoLaneSerialMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.Esp32WebSocketDirectMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.Esp32WebSocketDirectSettings
import io.github.raginlundf.racingmanager.infrastructure.gateway.transport.WebSocketRaceDeviceTransport
import io.github.raginlundf.racingmanager.infrastructure.repositories.RaceDeviceSettingsRepository
import io.ktor.server.application.Application

private val logger = KotlinLogging.logger {}

private const val DEFAULT_ENDPOINT = "ws://raspberrypi.local:8080/race"

/**
 * Builds the gateway for [settings]: the in-process simulator for
 * [RaceDeviceMode.SIMULATED], a WebSocket connection to a real Raspberry Pi for
 * [RaceDeviceMode.HARDWARE], or a local serial port for
 * [RaceDeviceMode.ARDUINO_TWO_LANE].
 */
fun buildRaceDeviceGateway(settings: RaceDeviceSettings): CloseableMeasurementGateway {
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

        RaceDeviceMode.ARDUINO_TWO_LANE -> {
            val arduino = settings.arduino ?: ArduinoTwoLaneSettings()
            logger.info { "Race device: Arduino two-lane on ${arduino.portName} at ${arduino.baudRate} baud" }
            TwoLaneSerialMeasurementGateway(
                port = JSerialCommLine(portName = arduino.portName, baudRate = arduino.baudRate),
                config = arduino,
                laneTimeoutMs = settings.finishTimeoutMs,
                rawLog = RawTimingLog(path = arduino.rawLogPath),
            )
        }

        RaceDeviceMode.ESP32_WEBSOCKET_DIRECT -> {
            val esp32 = settings.esp32 ?: Esp32WebSocketDirectSettings()
            logger.info { "Race device: ESP32 WebSocket Direct Connect, expecting ${esp32.expectedDeviceIds}" }
            Esp32WebSocketDirectMeasurementGateway(
                settings = esp32,
                laneTimeoutMs = settings.finishTimeoutMs,
                rawLog = RawTimingLog(path = esp32.rawLogPath),
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
    return RaceDeviceSettings(
        mode = mode,
        endpoint = endpoint,
        finishTimeoutMs = finishTimeoutMs,
        arduino = arduinoSettingsFromConfig(),
        esp32 = esp32SettingsFromConfig(),
    )
}

/** Arduino startup defaults; every value falls back to the suggested one from
    `.plan/Adruino-impl.md` §7.1 so an install only has to name the port. */
private fun Application.arduinoSettingsFromConfig(): ArduinoTwoLaneSettings {
    val config = environment.config
    fun value(key: String): String? {
        return config.propertyOrNull(path = "racingmanager.racedevice.arduino.$key")?.getString()?.takeIf {
            it.isNotBlank()
        }
    }
    val defaults = ArduinoTwoLaneSettings()
    return ArduinoTwoLaneSettings(
        portName = value(key = "portName") ?: defaults.portName,
        baudRate = value(key = "baudRate")?.toInt() ?: defaults.baudRate,
        readyTimeoutMs = value(key = "readyTimeoutMs")?.toLong() ?: defaults.readyTimeoutMs,
        falseStartWindowMs = value(key = "falseStartWindowMs")?.toLong() ?: defaults.falseStartWindowMs,
        finishSemantics = value(key = "finishSemantics")?.let { FinishSemantics.from(value = it) }
            ?: defaults.finishSemantics,
        rawLogPath = value(key = "rawLogPath") ?: defaults.rawLogPath,
    )
}

/** ESP32 direct-connect startup defaults; every value falls back to
    [Esp32WebSocketDirectSettings]'s concrete-case defaults so an install only has
    to override what differs (e.g. a non-default device-id list). */
private fun Application.esp32SettingsFromConfig(): Esp32WebSocketDirectSettings {
    val config = environment.config
    fun value(key: String): String? {
        return config.propertyOrNull(path = "racingmanager.racedevice.esp32.$key")?.getString()?.takeIf {
            it.isNotBlank()
        }
    }
    val defaults = Esp32WebSocketDirectSettings()
    return Esp32WebSocketDirectSettings(
        expectedDeviceIds = value(key = "expectedDeviceIds")?.split(",")?.map { it.trim() }
            ?: defaults.expectedDeviceIds,
        registerTimeoutMs = value(key = "registerTimeoutMs")?.toLong() ?: defaults.registerTimeoutMs,
        useRaceControlHandshake = value(key = "useRaceControlHandshake")?.toBoolean()
            ?: defaults.useRaceControlHandshake,
        useTimeSync = value(key = "useTimeSync")?.toBoolean() ?: defaults.useTimeSync,
        useDeviceHeartbeat = value(key = "useDeviceHeartbeat")?.toBoolean() ?: defaults.useDeviceHeartbeat,
        heartbeatTimeoutMs = value(key = "heartbeatTimeoutMs")?.toLong() ?: defaults.heartbeatTimeoutMs,
        armTimeoutMs = value(key = "armTimeoutMs")?.toLong() ?: defaults.armTimeoutMs,
        timeSyncRounds = value(key = "timeSyncRounds")?.toInt() ?: defaults.timeSyncRounds,
        rawLogPath = value(key = "rawLogPath") ?: defaults.rawLogPath,
    )
}
