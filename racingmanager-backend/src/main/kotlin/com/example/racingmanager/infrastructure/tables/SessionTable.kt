package com.example.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object SessionTable : Table("sessions") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val lastAccessedAt = timestamp("last_accessed_at")

    override val primaryKey = PrimaryKey(id)
}
