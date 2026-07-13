package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.sync.PairedInstanceEntity
import io.github.raginlundf.racingmanager.domain.sync.PairedInstanceStatus
import io.github.raginlundf.racingmanager.infrastructure.tables.PairedInstanceTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import java.util.UUID

class PairedInstanceRepository {

    fun findById(id: UUID): PairedInstanceEntity? = transaction {
        PairedInstanceTable.selectAll().where { PairedInstanceTable.id eq id }.singleOrNull()?.toEntity()
    }

    fun findAllForTenant(tenantId: UUID): List<PairedInstanceEntity> = transaction {
        PairedInstanceTable.selectAll().where { PairedInstanceTable.tenantId eq tenantId }.map { it.toEntity() }
    }

    fun insert(instance: PairedInstanceEntity) = transaction {
        PairedInstanceTable.insert {
            it[id] = instance.id
            it[tenantId] = instance.tenantId
            it[status] = instance.status.name
            it[pairedAt] = instance.pairedAt
            it[lastSyncAt] = instance.lastSyncAt
        }
    }

    fun updateStatus(id: UUID, status: PairedInstanceStatus) = transaction {
        PairedInstanceTable.update(where = { PairedInstanceTable.id eq id }) { it[PairedInstanceTable.status] = status.name }
    }

    fun updateLastSyncAt(id: UUID, lastSyncAt: Instant) = transaction {
        PairedInstanceTable.update(where = { PairedInstanceTable.id eq id }) { it[PairedInstanceTable.lastSyncAt] = lastSyncAt }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toEntity() = PairedInstanceEntity(
        id = this[PairedInstanceTable.id],
        tenantId = this[PairedInstanceTable.tenantId],
        status = PairedInstanceStatus.valueOf(this[PairedInstanceTable.status]),
        pairedAt = this[PairedInstanceTable.pairedAt],
        lastSyncAt = this[PairedInstanceTable.lastSyncAt],
    )
}
