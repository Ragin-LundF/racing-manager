package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object MembershipTable : Table("memberships") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val tenantId = javaUUID("tenant_id")
    val status = varchar("status", 50)
    val role = varchar("role", 50)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
