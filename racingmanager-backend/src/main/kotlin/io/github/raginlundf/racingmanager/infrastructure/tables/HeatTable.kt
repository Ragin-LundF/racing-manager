package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object HeatTable : Table("heats") {
    val id = javaUUID("id")
    val eventId = javaUUID("event_id")
    val round = integer("round")
    val heatNumber = integer("heat_number")
    val status = varchar("status", 50)
    val createdAt = timestamp("created_at")
    val armedAt = timestamp("armed_at").nullable()
    val startedAt = timestamp("started_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object HeatLaneTable : Table("heat_lanes") {
    val id = javaUUID("id")
    val heatId = javaUUID("heat_id")
    val lane = integer("lane")
    val participantId = javaUUID("participant_id")
    val participantStartNumber = integer("participant_start_number")
    val participantFirstName = varchar("participant_first_name", 255)
    val participantLastName = varchar("participant_last_name", 255)

    override val primaryKey = PrimaryKey(id)
}

object MeasurementTable : Table("measurements") {
    val id = javaUUID("id")
    val heatId = javaUUID("heat_id")
    val lane = integer("lane")
    val durationNanos = long("duration_nanos")
    val outcome = varchar("outcome", 50)
    val receivedAt = timestamp("received_at")

    override val primaryKey = PrimaryKey(id)
}
