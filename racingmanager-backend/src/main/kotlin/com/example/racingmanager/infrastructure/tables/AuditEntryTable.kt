package com.example.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object AuditEntryTable : Table("audit_entries") {
    val id = javaUUID("id")
    val actorId = javaUUID("actor_id").nullable()
    val action = varchar("action", 255)
    val targetType = varchar("target_type", 100).nullable()
    val targetId = javaUUID("target_id").nullable()
    val summary = text("summary").nullable()
    val details = text("details").nullable()
    val correlationId = varchar("correlation_id", 255).nullable()
    val occurredAt = timestamp("occurred_at")

    override val primaryKey = PrimaryKey(id)
}
