package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.participant.EventSeedEntity
import io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity
import io.github.raginlundf.racingmanager.domain.participant.ParticipantStatus
import io.github.raginlundf.racingmanager.domain.participant.VehicleEntity
import io.github.raginlundf.racingmanager.infrastructure.tables.EventSeedTable
import io.github.raginlundf.racingmanager.infrastructure.tables.ParticipantTable
import io.github.raginlundf.racingmanager.infrastructure.tables.VehicleTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class ParticipantRepository {

    fun findByEventId(eventId: UUID): List<ParticipantEntity> = transaction {
        val rows = ParticipantTable.selectAll()
            .where { ParticipantTable.eventId eq eventId }
            .orderBy(ParticipantTable.sortOrder to SortOrder.ASC)
            .toList()

        // One query for all vehicles instead of one per participant (was an N+1).
        val vehiclesByParticipant = VehicleTable.selectAll()
            .where { VehicleTable.participantId inList rows.map { it[ParticipantTable.id] } }
            .associate { v ->
                v[VehicleTable.participantId] to VehicleEntity(
                    id = v[VehicleTable.id],
                    participantId = v[VehicleTable.participantId],
                    name = v[VehicleTable.name],
                    category = v[VehicleTable.category],
                )
            }

        rows.map { row ->
            val participantId = row[ParticipantTable.id]
            ParticipantEntity(
                id = participantId,
                eventId = row[ParticipantTable.eventId],
                startNumber = row[ParticipantTable.startNumber],
                firstName = row[ParticipantTable.firstName],
                lastName = row[ParticipantTable.lastName],
                club = row[ParticipantTable.club],
                status = ParticipantStatus.valueOf(row[ParticipantTable.status]),
                sortOrder = row[ParticipantTable.sortOrder],
                vehicle = vehiclesByParticipant[participantId],
                createdAt = row[ParticipantTable.createdAt],
                updatedAt = row[ParticipantTable.updatedAt],
            )
        }
    }

    fun findById(id: UUID): ParticipantEntity? = transaction {
        ParticipantTable.selectAll().where { ParticipantTable.id eq id }
            .singleOrNull()
            ?.let { row ->
                val vehicle = VehicleTable.selectAll()
                    .where { VehicleTable.participantId eq id }
                    .singleOrNull()
                    ?.let { v ->
                        VehicleEntity(
                            id = v[VehicleTable.id],
                            participantId = v[VehicleTable.participantId],
                            name = v[VehicleTable.name],
                            category = v[VehicleTable.category],
                        )
                    }
                ParticipantEntity(
                    id = row[ParticipantTable.id],
                    eventId = row[ParticipantTable.eventId],
                    startNumber = row[ParticipantTable.startNumber],
                    firstName = row[ParticipantTable.firstName],
                    lastName = row[ParticipantTable.lastName],
                    club = row[ParticipantTable.club],
                    status = ParticipantStatus.valueOf(row[ParticipantTable.status]),
                    sortOrder = row[ParticipantTable.sortOrder],
                    vehicle = vehicle,
                    createdAt = row[ParticipantTable.createdAt],
                    updatedAt = row[ParticipantTable.updatedAt],
                )
            }
    }

    fun findByEventIdAndStartNumber(eventId: UUID, startNumber: Int): ParticipantEntity? = transaction {
        ParticipantTable.selectAll()
            .where { (ParticipantTable.eventId eq eventId) and (ParticipantTable.startNumber eq startNumber) }
            .singleOrNull()
            ?.let { row ->
                ParticipantEntity(
                    id = row[ParticipantTable.id],
                    eventId = row[ParticipantTable.eventId],
                    startNumber = row[ParticipantTable.startNumber],
                    firstName = row[ParticipantTable.firstName],
                    lastName = row[ParticipantTable.lastName],
                    club = row[ParticipantTable.club],
                    status = ParticipantStatus.valueOf(row[ParticipantTable.status]),
                    sortOrder = row[ParticipantTable.sortOrder],
                    vehicle = null,
                    createdAt = row[ParticipantTable.createdAt],
                    updatedAt = row[ParticipantTable.updatedAt],
                )
            }
    }

    fun insert(participant: ParticipantEntity) = transaction {
        ParticipantTable.insert {
            it[id] = participant.id
            it[eventId] = participant.eventId
            it[startNumber] = participant.startNumber
            it[firstName] = participant.firstName
            it[lastName] = participant.lastName
            it[club] = participant.club
            it[status] = participant.status.name
            it[sortOrder] = participant.sortOrder
            it[createdAt] = participant.createdAt
            it[updatedAt] = participant.updatedAt
        }
        participant.vehicle?.let { vehicle ->
            VehicleTable.insert {
                it[id] = vehicle.id
                it[participantId] = vehicle.participantId
                it[name] = vehicle.name
                it[category] = vehicle.category
            }
        }
    }

    fun update(participant: ParticipantEntity) = transaction {
        ParticipantTable.update({ ParticipantTable.id eq participant.id }) {
            it[startNumber] = participant.startNumber
            it[firstName] = participant.firstName
            it[lastName] = participant.lastName
            it[club] = participant.club
            it[status] = participant.status.name
            it[sortOrder] = participant.sortOrder
            it[updatedAt] = participant.updatedAt
        }
    }

    fun updateSortOrders(updates: List<Pair<UUID, Int>>) = transaction {
        updates.forEach { (id, order) ->
            ParticipantTable.update({ ParticipantTable.id eq id }) {
                it[ParticipantTable.sortOrder] = order
            }
        }
    }

    fun deleteByEventId(eventId: UUID) = transaction {
        val participantIds = ParticipantTable.selectAll()
            .where { ParticipantTable.eventId eq eventId }
            .map { it[ParticipantTable.id] }
        participantIds.forEach { pid ->
            VehicleTable.deleteWhere { VehicleTable.participantId eq pid }
        }
        ParticipantTable.deleteWhere { ParticipantTable.eventId eq eventId }
    }

    fun countByEventId(eventId: UUID): Long = transaction {
        ParticipantTable.selectAll().where { ParticipantTable.eventId eq eventId }.count()
    }

    fun maxStartNumberByEventId(eventId: UUID): Int? = transaction {
        ParticipantTable.selectAll()
            .where { ParticipantTable.eventId eq eventId }
            .maxOfOrNull { it[ParticipantTable.startNumber] }
    }

    fun deleteAll() = transaction {
        VehicleTable.deleteAll()
        ParticipantTable.deleteAll()
    }

    // Event seed
    fun findSeedByEventId(eventId: UUID): EventSeedEntity? = transaction {
        EventSeedTable.selectAll().where { EventSeedTable.eventId eq eventId }
            .singleOrNull()
            ?.let { row ->
                EventSeedEntity(
                    eventId = row[EventSeedTable.eventId],
                    seed = row[EventSeedTable.seed],
                    randomizedAt = row[EventSeedTable.randomizedAt],
                    randomizedBy = row[EventSeedTable.randomizedBy],
                )
            }
    }

    fun upsertSeed(seed: EventSeedEntity) = transaction {
        val existing = EventSeedTable.selectAll().where { EventSeedTable.eventId eq seed.eventId }.singleOrNull()
        if (existing != null) {
            EventSeedTable.update({ EventSeedTable.eventId eq seed.eventId }) {
                it[EventSeedTable.seed] = seed.seed
                it[randomizedAt] = seed.randomizedAt
                it[randomizedBy] = seed.randomizedBy
            }
        } else {
            EventSeedTable.insert {
                it[eventId] = seed.eventId
                it[EventSeedTable.seed] = seed.seed
                it[randomizedAt] = seed.randomizedAt
                it[randomizedBy] = seed.randomizedBy
            }
        }
    }
}
