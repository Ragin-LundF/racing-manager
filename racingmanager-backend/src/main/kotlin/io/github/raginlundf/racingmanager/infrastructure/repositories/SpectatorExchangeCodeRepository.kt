package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.spectator.SpectatorExchangeCodeEntity
import io.github.raginlundf.racingmanager.infrastructure.tables.SpectatorExchangeCodeTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import java.util.UUID

class SpectatorExchangeCodeRepository {

    fun insert(entry: SpectatorExchangeCodeEntity) {
        transaction {
            SpectatorExchangeCodeTable.insert {
                it[id] = entry.id
                it[tenantId] = entry.tenantId
                it[eventId] = entry.eventId
                it[token] = entry.token
                it[createdAt] = entry.createdAt
                it[expiresAt] = entry.expiresAt
                it[consumed] = entry.consumed
            }
        }
    }

    /** Atomically consumes the code if it exists, is unexpired and unused —
        returns null otherwise so a code can never be traded for a token
        twice, nor after its short exchange window has passed. */
    fun consume(id: UUID, now: Instant): SpectatorExchangeCodeEntity? {
        return transaction {
            val row = SpectatorExchangeCodeTable.selectAll().where { SpectatorExchangeCodeTable.id eq id }.singleOrNull()
                ?: return@transaction null
            if (row[SpectatorExchangeCodeTable.consumed] || now > row[SpectatorExchangeCodeTable.expiresAt]) {
                return@transaction null
            }
            SpectatorExchangeCodeTable.update({ SpectatorExchangeCodeTable.id eq id }) {
                it[consumed] = true
            }
            SpectatorExchangeCodeEntity(
                id = row[SpectatorExchangeCodeTable.id],
                tenantId = row[SpectatorExchangeCodeTable.tenantId],
                eventId = row[SpectatorExchangeCodeTable.eventId],
                token = row[SpectatorExchangeCodeTable.token],
                createdAt = row[SpectatorExchangeCodeTable.createdAt],
                expiresAt = row[SpectatorExchangeCodeTable.expiresAt],
                consumed = true,
            )
        }
    }
}
