package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object SyncedResultTable : Table("synced_results") {
    val id = javaUUID("id")
    val eventId = javaUUID("event_id")
    val tenantId = javaUUID("tenant_id")
    val localInstanceId = javaUUID("local_instance_id")
    val resultsJson = text("results_json")
    val syncedAt = timestamp("synced_at")

    override val primaryKey = PrimaryKey(id)
}
