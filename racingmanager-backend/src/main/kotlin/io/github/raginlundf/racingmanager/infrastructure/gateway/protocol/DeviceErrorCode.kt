package io.github.raginlundf.racingmanager.infrastructure.gateway.protocol

import kotlinx.serialization.Serializable

/** Machine-readable device error codes (raspberry.md §4 "error"). */
@Serializable
enum class DeviceErrorCode {
    INVALID_STATE,
    UNKNOWN_RACE,
    HARDWARE_FAILURE,
    DUPLICATE_COMMAND,
    SENSOR_STUCK,
}
