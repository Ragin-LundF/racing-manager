package io.github.raginlundf.racingmanager.domain.event

import kotlin.time.Instant
import java.util.UUID

data class EventEntity(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val status: EventStatus = EventStatus.DRAFT,
    val settings: EventSettings = EventSettings(),
    val version: Long = 0L,
    val createdBy: UUID,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val activatedAt: Instant? = null,
)
