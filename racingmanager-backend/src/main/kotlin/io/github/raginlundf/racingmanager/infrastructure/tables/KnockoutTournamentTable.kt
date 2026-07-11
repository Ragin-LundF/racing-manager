package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object KnockoutTournamentTable : Table("knockout_tournaments") {
    val id = javaUUID("id")
    val eventId = javaUUID("event_id")
    val status = varchar("status", 50)
    val pairingMode = varchar("pairing_mode", 50)
    val qualificationId = javaUUID("qualification_id")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").nullable()
    val finalizedAt = timestamp("finalized_at").nullable()
    val finalizedBy = javaUUID("finalized_by").nullable()
    override val primaryKey = PrimaryKey(id)
}

object KnockoutMatchTable : Table("knockout_matches") {
    val id = javaUUID("id")
    val tournamentId = javaUUID("tournament_id")
    val roundNumber = integer("round_number")
    val matchNumber = integer("match_number")
    val participant1Id = javaUUID("participant1_id").nullable()
    val participant2Id = javaUUID("participant2_id").nullable()
    val winnerId = javaUUID("winner_id").nullable()
    val heatId = javaUUID("heat_id").nullable()
    val status = varchar("status", 50)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
