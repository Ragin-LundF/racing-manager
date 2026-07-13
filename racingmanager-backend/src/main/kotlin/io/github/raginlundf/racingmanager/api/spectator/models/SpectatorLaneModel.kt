package io.github.raginlundf.racingmanager.api.spectator.models

import kotlinx.serialization.Serializable

@Serializable
data class SpectatorLaneModel(
    val lane: Int,
    val participantId: String,
    val participantStartNumber: Int,
    val participantFirstName: String,
    val participantLastName: String,
    val durationNanos: Long? = null,
    val outcome: String? = null,
)
