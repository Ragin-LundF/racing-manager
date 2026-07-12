package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.infrastructure.tables.AuditEntryTable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class AuditRepository {

    fun insert(entry: AuditEntryEntity) = transaction {
        AuditEntryTable.insert {
            it[id] = entry.id
            it[actorId] = entry.actorId
            it[action] = entry.action
            it[targetType] = entry.targetType
            it[targetId] = entry.targetId
            it[summary] = entry.summary
            it[details] = entry.details
            it[correlationId] = entry.correlationId
            it[occurredAt] = entry.occurredAt
        }
    }

    fun query(
        action: String? = null,
        targetType: String? = null,
        targetId: UUID? = null,
        actorId: UUID? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): List<AuditEntryEntity> = transaction {
        val conditions = mutableListOf<Op<Boolean>>()
        if (action != null) conditions.add(AuditEntryTable.action eq action)
        if (targetType != null) conditions.add(AuditEntryTable.targetType eq targetType)
        if (targetId != null) conditions.add(AuditEntryTable.targetId eq targetId)
        if (actorId != null) conditions.add(AuditEntryTable.actorId eq actorId)

        AuditEntryTable.selectAll()
            .let { query ->
                if (conditions.isEmpty()) query
                else query.where { conditions.reduce { a, b -> a and b } }
            }
            .orderBy(AuditEntryTable.occurredAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { row ->
                AuditEntryEntity(
                    id = row[AuditEntryTable.id],
                    actorId = row[AuditEntryTable.actorId],
                    action = row[AuditEntryTable.action],
                    targetType = row[AuditEntryTable.targetType],
                    targetId = row[AuditEntryTable.targetId],
                    summary = row[AuditEntryTable.summary],
                    details = row[AuditEntryTable.details],
                    correlationId = row[AuditEntryTable.correlationId],
                    occurredAt = row[AuditEntryTable.occurredAt],
                )
            }
    }
}
