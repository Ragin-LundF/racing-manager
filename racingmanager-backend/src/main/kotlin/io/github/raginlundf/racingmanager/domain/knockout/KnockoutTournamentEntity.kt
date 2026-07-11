package io.github.raginlundf.racingmanager.domain.knockout

import kotlin.time.Instant
import java.util.UUID

data class KnockoutTournamentEntity(
    val id: UUID,
    val eventId: UUID,
    val status: KnockoutStatus = KnockoutStatus.PENDING,
    val pairingMode: PairingMode,
    val qualificationId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val finalizedAt: Instant? = null,
    val finalizedBy: UUID? = null,
)
