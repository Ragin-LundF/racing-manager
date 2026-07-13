package io.github.raginlundf.racingmanager.infrastructure.gateway.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Commands the PC application sends to the race device (raspberry.md §3).
    Serialized polymorphically with a `type` discriminator. */
@Serializable
sealed interface DeviceCommand {
    @Serializable
    @SerialName("hello")
    data object Hello : DeviceCommand

    @Serializable
    @SerialName("prepareRace")
    data class PrepareRace(
        val lanes: List<Int>,
        val startMode: String = "shared-gate",
        val finishTimeoutMs: Long,
    ) : DeviceCommand

    @Serializable
    @SerialName("startRace")
    data object StartRace : DeviceCommand

    @Serializable
    @SerialName("abortRace")
    data object AbortRace : DeviceCommand

    @Serializable
    @SerialName("reset")
    data object Reset : DeviceCommand

    @Serializable
    @SerialName("ping")
    data object Ping : DeviceCommand
}
