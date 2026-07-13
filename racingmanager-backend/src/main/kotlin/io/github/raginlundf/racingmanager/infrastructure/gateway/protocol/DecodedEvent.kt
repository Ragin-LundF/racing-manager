package io.github.raginlundf.racingmanager.infrastructure.gateway.protocol

/** A decoded event frame: the envelope meta plus the typed [event]. [messageId]
    is used for duplicate detection (raspberry.md §7). */
data class DecodedEvent(
    val messageId: String,
    val raceId: String?,
    val event: DeviceEvent,
)
