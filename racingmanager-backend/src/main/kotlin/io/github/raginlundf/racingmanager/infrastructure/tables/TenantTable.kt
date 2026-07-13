package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object TenantTable : Table("tenants") {
    val id = javaUUID("id")
    val slug = varchar("slug", 100).nullable()
    val displayName = varchar("display_name", 255)
    val status = varchar("status", 50)
    val settings = text("settings").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
