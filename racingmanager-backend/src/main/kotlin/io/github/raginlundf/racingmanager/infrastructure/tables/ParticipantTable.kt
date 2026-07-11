package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object ParticipantTable : Table("participants") {
    val id = javaUUID("id")
    val eventId = javaUUID("event_id")
    val startNumber = integer("start_number")
    val firstName = varchar("first_name", 255)
    val lastName = varchar("last_name", 255)
    val club = varchar("club", 255).nullable()
    val status = varchar("status", 50)
    val sortOrder = integer("sort_order").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object VehicleTable : Table("vehicles") {
    val id = javaUUID("id")
    val participantId = javaUUID("participant_id")
    val name = varchar("name", 255)
    val category = varchar("category", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

object EventSeedTable : Table("event_seeds") {
    val eventId = javaUUID("event_id")
    val seed = long("seed")
    val randomizedAt = timestamp("randomized_at")
    val randomizedBy = javaUUID("randomized_by")

    override val primaryKey = PrimaryKey(eventId)
}
