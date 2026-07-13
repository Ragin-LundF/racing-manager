package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object EventTable : Table("events") {
    val id = javaUUID("id")
    val tenantId = javaUUID("tenant_id")
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val status = varchar("status", 50)
    val laneType = varchar("lane_type", 50)
    val measurementType = varchar("measurement_type", 50)
    val maxParticipants = integer("max_participants").nullable()
    val version = long("version")
    val createdBy = javaUUID("created_by")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").nullable()
    val activatedAt = timestamp("activated_at").nullable()
    val originTenantId = javaUUID("origin_tenant_id").nullable()
    val originPackageId = javaUUID("origin_package_id").nullable()
    val lockedForSync = bool("locked_for_sync").default(false)
    val syncStatus = varchar("sync_status", 50).nullable()

    override val primaryKey = PrimaryKey(id)
}
