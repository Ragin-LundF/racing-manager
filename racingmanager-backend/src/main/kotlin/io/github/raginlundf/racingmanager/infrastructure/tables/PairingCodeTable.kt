package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object PairingCodeTable : Table("pairing_codes") {
    val id = javaUUID("id")
    val tenantId = javaUUID("tenant_id")
    val expiresAt = timestamp("expires_at")
    val consumed = bool("consumed")

    override val primaryKey = PrimaryKey(id)
}
