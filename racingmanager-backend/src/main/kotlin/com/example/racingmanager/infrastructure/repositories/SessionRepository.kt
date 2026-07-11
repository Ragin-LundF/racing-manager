package com.example.racingmanager.infrastructure.repositories

import com.example.racingmanager.domain.session.SessionEntity
import com.example.racingmanager.infrastructure.tables.SessionTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import java.util.UUID

class SessionRepository {

    fun findById(id: UUID): SessionEntity? = transaction {
        SessionTable.selectAll().where { SessionTable.id eq id }
            .singleOrNull()
            ?.let { row ->
                SessionEntity(
                    id = row[SessionTable.id],
                    userId = row[SessionTable.userId],
                    createdAt = row[SessionTable.createdAt],
                    expiresAt = row[SessionTable.expiresAt],
                    lastAccessedAt = row[SessionTable.lastAccessedAt],
                )
            }
    }

    fun insert(session: SessionEntity) = transaction {
        SessionTable.insert {
            it[id] = session.id
            it[userId] = session.userId
            it[createdAt] = session.createdAt
            it[expiresAt] = session.expiresAt
            it[lastAccessedAt] = session.lastAccessedAt
        }
    }

    fun updateLastAccessed(id: UUID, now: Instant) = transaction {
        SessionTable.update({ SessionTable.id eq id }) {
            it[lastAccessedAt] = now
        }
    }

    fun deleteById(id: UUID) = transaction {
        SessionTable.deleteWhere { SessionTable.id eq id }
    }

    fun deleteByUserId(userId: UUID) = transaction {
        SessionTable.deleteWhere { SessionTable.userId eq userId }
    }

    fun deleteExpired(now: Instant) = transaction {
        SessionTable.deleteWhere { SessionTable.expiresAt less now }
    }
}
