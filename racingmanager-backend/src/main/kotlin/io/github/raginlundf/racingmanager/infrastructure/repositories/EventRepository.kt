package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.event.LaneType
import io.github.raginlundf.racingmanager.domain.event.MeasurementType
import io.github.raginlundf.racingmanager.infrastructure.tables.EventTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class EventRepository {

    fun findById(id: UUID): EventEntity? = transaction {
        EventTable.selectAll().where { EventTable.id eq id }
            .singleOrNull()
            ?.let { row ->
                EventEntity(
                    id = row[EventTable.id],
                    name = row[EventTable.name],
                    description = row[EventTable.description],
                    status = EventStatus.valueOf(row[EventTable.status]),
                    settings = EventSettings(
                        laneType = LaneType.valueOf(row[EventTable.laneType]),
                        measurementType = MeasurementType.valueOf(row[EventTable.measurementType]),
                        maxParticipants = row[EventTable.maxParticipants],
                    ),
                    version = row[EventTable.version],
                    createdBy = row[EventTable.createdBy],
                    createdAt = row[EventTable.createdAt],
                    updatedAt = row[EventTable.updatedAt],
                    activatedAt = row[EventTable.activatedAt],
                )
            }
    }

    fun findAll(): List<EventEntity> = transaction {
        EventTable.selectAll().map { row ->
            EventEntity(
                id = row[EventTable.id],
                name = row[EventTable.name],
                description = row[EventTable.description],
                status = EventStatus.valueOf(row[EventTable.status]),
                settings = EventSettings(
                    laneType = LaneType.valueOf(row[EventTable.laneType]),
                    measurementType = MeasurementType.valueOf(row[EventTable.measurementType]),
                    maxParticipants = row[EventTable.maxParticipants],
                ),
                version = row[EventTable.version],
                createdBy = row[EventTable.createdBy],
                createdAt = row[EventTable.createdAt],
                updatedAt = row[EventTable.updatedAt],
                activatedAt = row[EventTable.activatedAt],
            )
        }
    }

    fun insert(event: EventEntity) = transaction {
        EventTable.insert {
            it[id] = event.id
            it[name] = event.name
            it[description] = event.description
            it[status] = event.status.name
            it[laneType] = event.settings.laneType.name
            it[measurementType] = event.settings.measurementType.name
            it[maxParticipants] = event.settings.maxParticipants
            it[version] = event.version
            it[createdBy] = event.createdBy
            it[createdAt] = event.createdAt
            it[updatedAt] = event.updatedAt
            it[activatedAt] = event.activatedAt
        }
    }

    fun update(event: EventEntity): Boolean = transaction {
        val count = EventTable.update({ EventTable.id eq event.id and (EventTable.version eq event.version - 1) }) {
            it[name] = event.name
            it[description] = event.description
            it[status] = event.status.name
            it[laneType] = event.settings.laneType.name
            it[measurementType] = event.settings.measurementType.name
            it[maxParticipants] = event.settings.maxParticipants
            it[version] = event.version
            it[updatedAt] = event.updatedAt
            it[activatedAt] = event.activatedAt
        }
        count > 0
    }

    fun deleteAll() = transaction {
        EventTable.deleteAll()
    }
}
