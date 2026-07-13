package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.event.LaneType
import io.github.raginlundf.racingmanager.domain.event.MeasurementType
import io.github.raginlundf.racingmanager.domain.event.SyncStatus
import io.github.raginlundf.racingmanager.infrastructure.tables.EventTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class EventRepository {

    fun findById(id: UUID): EventEntity? {
        return transaction {
            EventTable.selectAll().where { EventTable.id eq id }
                .singleOrNull()
                ?.toEventEntity()
        }
    }

    fun findAll(): List<EventEntity> {
        return transaction {
            EventTable.selectAll().map { it.toEventEntity() }
        }
    }

    fun insert(event: EventEntity) {
        transaction {
            EventTable.insert {
                it[id] = event.id
                it[tenantId] = event.tenantId
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
                it[originTenantId] = event.originTenantId
                it[originPackageId] = event.originPackageId
                it[lockedForSync] = event.lockedForSync
                it[syncStatus] = event.syncStatus?.name
            }
        }
    }

    fun update(event: EventEntity): Boolean {
        return transaction {
            val count =
                EventTable.update(where = { EventTable.id eq event.id and (EventTable.version eq event.version - 1) }) {
                    it[name] = event.name
                    it[description] = event.description
                    it[status] = event.status.name
                    it[laneType] = event.settings.laneType.name
                    it[measurementType] = event.settings.measurementType.name
                    it[maxParticipants] = event.settings.maxParticipants
                    it[version] = event.version
                    it[updatedAt] = event.updatedAt
                    it[activatedAt] = event.activatedAt
                    it[lockedForSync] = event.lockedForSync
                    it[syncStatus] = event.syncStatus?.name
                }
            count > 0
        }
    }

    fun delete(id: UUID): Boolean {
        return transaction {
            EventTable.deleteWhere { EventTable.id eq id } > 0
        }
    }

    /** Defense-in-depth tenant filter: returns the event only if it belongs to
    [tenantId], so a route-level check that is missed or bypassed cannot
    leak another tenant's event through this query alone. */
    fun findByIdForTenant(id: UUID, tenantId: UUID): EventEntity? {
        return transaction {
            EventTable.selectAll().where { (EventTable.id eq id) and (EventTable.tenantId eq tenantId) }
                .singleOrNull()
                ?.toEventEntity()
        }
    }

    fun findAllForTenant(tenantId: UUID): List<EventEntity> {
        return transaction {
            EventTable.selectAll().where { EventTable.tenantId eq tenantId }
                .map { it.toEventEntity() }
        }
    }

    fun deleteAll() {
        transaction {
            EventTable.deleteAll()
        }
    }

    private fun ResultRow.toEventEntity(): EventEntity {
        return EventEntity(
            id = this[EventTable.id],
            tenantId = this[EventTable.tenantId],
            name = this[EventTable.name],
            description = this[EventTable.description],
            status = EventStatus.valueOf(this[EventTable.status]),
            settings = EventSettings(
                laneType = LaneType.valueOf(this[EventTable.laneType]),
                measurementType = MeasurementType.valueOf(this[EventTable.measurementType]),
                maxParticipants = this[EventTable.maxParticipants],
            ),
            version = this[EventTable.version],
            createdBy = this[EventTable.createdBy],
            createdAt = this[EventTable.createdAt],
            updatedAt = this[EventTable.updatedAt],
            activatedAt = this[EventTable.activatedAt],
            originTenantId = this[EventTable.originTenantId],
            originPackageId = this[EventTable.originPackageId],
            lockedForSync = this[EventTable.lockedForSync],
            syncStatus = this[EventTable.syncStatus]?.let { SyncStatus.valueOf(it) },
        )
    }
}
