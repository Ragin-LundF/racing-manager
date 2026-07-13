package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.sync.PairingCodeEntity
import io.github.raginlundf.racingmanager.infrastructure.tables.PairingCodeTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import java.util.UUID

class PairingCodeRepository {

    fun insert(code: PairingCodeEntity) = transaction {
        PairingCodeTable.insert {
            it[id] = code.id
            it[tenantId] = code.tenantId
            it[expiresAt] = code.expiresAt
            it[consumed] = code.consumed
        }
    }

    /** Atomically consumes the code if it exists, is unexpired and unused. */
    fun consume(id: UUID, now: Instant): PairingCodeEntity? = transaction {
        val row = PairingCodeTable.selectAll().where { PairingCodeTable.id eq id }.singleOrNull()
            ?: return@transaction null
        if (row[PairingCodeTable.consumed] || now > row[PairingCodeTable.expiresAt]) {
            return@transaction null
        }
        PairingCodeTable.update({ PairingCodeTable.id eq id }) { it[consumed] = true }
        PairingCodeEntity(
            id = row[PairingCodeTable.id],
            tenantId = row[PairingCodeTable.tenantId],
            expiresAt = row[PairingCodeTable.expiresAt],
            consumed = true,
        )
    }
}
