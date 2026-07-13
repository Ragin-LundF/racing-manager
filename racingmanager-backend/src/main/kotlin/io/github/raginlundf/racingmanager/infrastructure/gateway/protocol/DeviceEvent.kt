package io.github.raginlundf.racingmanager.infrastructure.gateway.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Events the race device sends to the PC application (raspberry.md §4).
    Serialized polymorphically with a `type` discriminator. Race times are based
    on the device's monotonic clock and carried in nanoseconds. */
@Serializable
sealed interface DeviceEvent {
    @Serializable
    @SerialName("helloAck")
    data class HelloAck(
        val deviceId: String,
        val firmwareVersion: String,
        val protocolVersion: Int,
        val capabilities: List<String> = emptyList(),
        val lanes: List<Int> = emptyList(),
    ) : DeviceEvent

    @Serializable
    @SerialName("raceReady")
    data class RaceReady(
        val lanes: List<Int>,
        val gateState: String,
    ) : DeviceEvent

    @Serializable
    @SerialName("raceStarted")
    data class RaceStarted(
        val startedLanes: List<Int>,
        val controllerMonotonicNs: Long,
    ) : DeviceEvent

    @Serializable
    @SerialName("finishDetected")
    data class FinishDetected(
        val lane: Int,
        val finishSequence: Int,
        val finishMonotonicNs: Long,
        val elapsedNs: Long,
        val sensorState: String = "blocked",
    ) : DeviceEvent

    @Serializable
    @SerialName("raceFinished")
    data class RaceFinished(
        val results: List<LaneResultPayload>,
        val completionReason: String,
    ) : DeviceEvent

    @Serializable
    @SerialName("error")
    data class DeviceError(
        val code: DeviceErrorCode,
        val message: String,
    ) : DeviceEvent

    @Serializable
    @SerialName("pong")
    data object Pong : DeviceEvent
}
