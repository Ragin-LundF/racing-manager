package io.github.raginlundf.racingmanager.api.racedevice.models

import kotlinx.serialization.Serializable

/** One serial port found on the machine running the backend, offered for selection. */
@Serializable
data class SerialPortModel(
    val name: String,
    val description: String,
)
