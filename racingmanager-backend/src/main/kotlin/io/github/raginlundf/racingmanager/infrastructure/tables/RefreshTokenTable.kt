package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object RefreshTokenTable : Table("refresh_tokens") {
    val id = javaUUID("id")
    val userId = javaUUID("user_id")
    val tenantId = javaUUID("tenant_id")
    val tokenVersion = integer("token_version")
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val revoked = bool("revoked")

    override val primaryKey = PrimaryKey(id)
}
