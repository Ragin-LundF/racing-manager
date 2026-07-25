package io.github.raginlundf.racingmanager.api.racedevice.models

import kotlinx.serialization.Serializable

/** Arduino two-lane serial options; only read when the mode is `ARDUINO_TWO_LANE`.
    `finishSemantics` crosses the wire as `TIMESTAMP` or `ELAPSED`. */
@Serializable
data class ArduinoSettingsModel(
    val portName: String,
    val baudRate: Int,
    val readyTimeoutMs: Long,
    val falseStartWindowMs: Long,
    val finishSemantics: String,
    val rawLogPath: String,
)
