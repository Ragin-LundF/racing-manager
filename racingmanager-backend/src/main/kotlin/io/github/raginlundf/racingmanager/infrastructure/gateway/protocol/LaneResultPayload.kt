package io.github.raginlundf.racingmanager.infrastructure.gateway.protocol

import kotlinx.serialization.Serializable

/** Per-lane outcome carried in [DeviceEvent.RaceFinished]. `status` is one of
    finished, timeout, not-started, aborted, invalid (raspberry.md §4). */
@Serializable
data class LaneResultPayload(
    val lane: Int,
    val status: String,
    val elapsedMs: Double? = null,
)
