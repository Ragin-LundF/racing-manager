package io.github.raginlundf.racingmanager.application.spectator

import java.util.UUID

data class SpectatorKnockoutMatch(
    val id: UUID,
    val roundNumber: Int,
    val matchNumber: Int,
    val participant1Id: UUID?,
    val participant2Id: UUID?,
    val winnerId: UUID?,
    val status: String,
    val isBye: Boolean,
)
