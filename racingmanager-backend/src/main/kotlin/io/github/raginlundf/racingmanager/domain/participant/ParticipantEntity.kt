package io.github.raginlundf.racingmanager.domain.participant

import kotlin.time.Instant
import java.util.UUID

data class ParticipantEntity(
    val id: UUID,
    val eventId: UUID,
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
    val status: ParticipantStatus = ParticipantStatus.ACTIVE,
    val sortOrder: Int? = null,
    val vehicle: VehicleEntity? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
)
