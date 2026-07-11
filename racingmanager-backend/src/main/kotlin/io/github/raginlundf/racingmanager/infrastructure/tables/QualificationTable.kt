package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object QualificationTable : Table("qualifications") {
    val id = javaUUID("id")
    val eventId = javaUUID("event_id")
    val status = varchar("status", 50)
    val numberOfRuns = integer("number_of_runs")
    val seed = long("seed")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").nullable()
    val finalizedAt = timestamp("finalized_at").nullable()
    val finalizedBy = javaUUID("finalized_by").nullable()
    override val primaryKey = PrimaryKey(id)
}
