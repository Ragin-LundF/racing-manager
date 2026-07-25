package io.github.raginlundf.racingmanager.api.racedevice.models

import kotlinx.serialization.Serializable

@Serializable
data class RaceDeviceSettingsResponseModel(
    val mode: String,
    val endpoint: String,
    val finishTimeoutMs: Long,
    val arduino: ArduinoSettingsModel? = null,
)
