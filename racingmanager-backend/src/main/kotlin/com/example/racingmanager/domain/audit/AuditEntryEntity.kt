package com.example.racingmanager.domain.audit

import kotlin.time.Instant
import java.util.UUID

data class AuditEntryEntity(
    val id: UUID,
    val actorId: UUID? = null,
    val action: String,
    val targetType: String? = null,
    val targetId: UUID? = null,
    val summary: String? = null,
    val details: String? = null,
    val correlationId: String? = null,
    val occurredAt: Instant,
)
