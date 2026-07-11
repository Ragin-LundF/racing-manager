package com.example.racingmanager.infrastructure.repositories

import com.example.racingmanager.domain.audit.AuditEntryEntity
import com.example.racingmanager.infrastructure.tables.AuditEntryTable
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

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
}
