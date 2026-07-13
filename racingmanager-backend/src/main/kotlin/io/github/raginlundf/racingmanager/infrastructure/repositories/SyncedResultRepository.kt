package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.sync.SyncedResultEntity
import io.github.raginlundf.racingmanager.infrastructure.tables.SyncedResultTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class SyncedResultRepository {

    fun insert(entry: SyncedResultEntity) {
        transaction {
            SyncedResultTable.insert {
                it[id] = entry.id
                it[eventId] = entry.eventId
                it[tenantId] = entry.tenantId
                it[localInstanceId] = entry.localInstanceId
                it[resultsJson] = entry.resultsJson
                it[syncedAt] = entry.syncedAt
            }
        }
    }

    fun findLatestByEventId(eventId: UUID): SyncedResultEntity? {
        return transaction {
            SyncedResultTable.selectAll().where { SyncedResultTable.eventId eq eventId }
                .orderBy(SyncedResultTable.syncedAt to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.let {
                    SyncedResultEntity(
                        id = it[SyncedResultTable.id],
                        eventId = it[SyncedResultTable.eventId],
                        tenantId = it[SyncedResultTable.tenantId],
                        localInstanceId = it[SyncedResultTable.localInstanceId],
                        resultsJson = it[SyncedResultTable.resultsJson],
                        syncedAt = it[SyncedResultTable.syncedAt],
                    )
                }
        }
    }
}
