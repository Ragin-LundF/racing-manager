package io.github.raginlundf.racingmanager.domain.knockout

import kotlin.time.Instant
import java.util.UUID

data class KnockoutMatchEntity(
    val id: UUID,
    val tournamentId: UUID,
    val roundNumber: Int,
    val matchNumber: Int,
    val participant1Id: UUID? = null,
    val participant2Id: UUID? = null,
    val winnerId: UUID? = null,
    val heatId: UUID? = null,
    val status: KnockoutMatchStatus = KnockoutMatchStatus.PLANNED,
    val createdAt: Instant,
)
