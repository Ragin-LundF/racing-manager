package io.github.raginlundf.racingmanager.infrastructure.gateway

import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.ArduinoTwoLaneSettings
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.Esp32WebSocketDirectSettings

/** The user-configurable race-device connection settings. Persisted (single row)
    so a local install can point the app at a real device from the UI instead of
    only via startup parameters. [endpoint] is only used when [mode] is
    [RaceDeviceMode.HARDWARE], [arduino] only when [mode] is
    [RaceDeviceMode.ARDUINO_TWO_LANE], [esp32] only when [mode] is
    [RaceDeviceMode.ESP32_WEBSOCKET_DIRECT] — switching modes keeps the other
    blocks around so the operator does not have to retype them. [finishTimeoutMs]
    is mode-independent: it is the per-lane deadline after which a lane counts as DNF. */
data class RaceDeviceSettings(
    val mode: RaceDeviceMode,
    val endpoint: String,
    val finishTimeoutMs: Long,
    val arduino: ArduinoTwoLaneSettings? = null,
    val esp32: Esp32WebSocketDirectSettings? = null,
)
