package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object PairedInstanceTable : Table("paired_instances") {
    val id = javaUUID("id")
    val tenantId = javaUUID("tenant_id")
    val status = varchar("status", 50)
    val pairedAt = timestamp("paired_at")
    val lastSyncAt = timestamp("last_sync_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
