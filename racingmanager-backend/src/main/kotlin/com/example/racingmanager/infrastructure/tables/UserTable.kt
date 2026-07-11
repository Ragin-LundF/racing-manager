package com.example.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object UserTable : Table("users") {
    val id = javaUUID("id")
    val username = varchar("username", 255)
    val passwordHash = varchar("password_hash", 512)
    val displayName = varchar("display_name", 255)
    val role = varchar("role", 50)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
