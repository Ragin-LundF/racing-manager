package io.github.raginlundf.racingmanager.application.audit

import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import java.util.UUID

class AuditService(
    private val auditRepository: AuditRepository,
) {
    fun query(
        action: String? = null,
        targetType: String? = null,
        targetId: UUID? = null,
        actorId: UUID? = null,
        tenantId: UUID? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): List<AuditEntryEntity> {
        return auditRepository.query(action, targetType, targetId, actorId, tenantId, limit, offset)
    }

    fun findByEventId(eventId: UUID): List<AuditEntryEntity> {
        return auditRepository.query(targetType = "Event", targetId = eventId)
    }
}
