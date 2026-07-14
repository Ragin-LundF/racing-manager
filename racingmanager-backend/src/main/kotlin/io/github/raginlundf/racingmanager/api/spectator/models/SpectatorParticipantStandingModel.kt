package io.github.raginlundf.racingmanager.api.spectator.models

import kotlinx.serialization.Serializable

@Serializable
data class SpectatorParticipantStandingModel(
    val participantId: String,
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val bestQualificationTimeNanos: Long? = null,
    val bestKnockoutTimeNanos: Long? = null,
    val state: String,
)
