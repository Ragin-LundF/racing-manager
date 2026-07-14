package io.github.raginlundf.racingmanager.application.spectator

import java.util.UUID

/**
 * Per-participant knockout-phase standing for the spectator view: name, best qualification and
 * best knockout times, and the current knockout [state] (WON / BYE / OUT / ACTIVE).
 */
data class SpectatorParticipantStanding(
    val participantId: UUID,
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val bestQualificationTimeNanos: Long?,
    val bestKnockoutTimeNanos: Long?,
    val state: String,
)
