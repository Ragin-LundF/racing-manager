package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.auth.RefreshTokenEntity
import io.github.raginlundf.racingmanager.infrastructure.tables.RefreshTokenTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import java.util.UUID

class RefreshTokenRepository {

    fun findById(id: UUID): RefreshTokenEntity? {
        return transaction {
            RefreshTokenTable.selectAll().where { RefreshTokenTable.id eq id }
                .singleOrNull()
                ?.let { row ->
                    RefreshTokenEntity(
                        id = row[RefreshTokenTable.id],
                        userId = row[RefreshTokenTable.userId],
                        tenantId = row[RefreshTokenTable.tenantId],
                        tokenVersion = row[RefreshTokenTable.tokenVersion],
                        createdAt = row[RefreshTokenTable.createdAt],
                        expiresAt = row[RefreshTokenTable.expiresAt],
                        revoked = row[RefreshTokenTable.revoked],
                    )
                }
        }
    }

    fun insert(token: RefreshTokenEntity) {
        transaction {
            RefreshTokenTable.insert {
                it[id] = token.id
                it[userId] = token.userId
                it[tenantId] = token.tenantId
                it[tokenVersion] = token.tokenVersion
                it[createdAt] = token.createdAt
                it[expiresAt] = token.expiresAt
                it[revoked] = token.revoked
            }
        }
    }

    fun revoke(id: UUID) {
        transaction {
            RefreshTokenTable.update({ RefreshTokenTable.id eq id }) {
                it[revoked] = true
            }
        }
    }

    /** Used for "logout everywhere" and after a password change. */
    fun revokeAllForUser(userId: UUID) {
        transaction {
            RefreshTokenTable.update({ RefreshTokenTable.userId eq userId }) {
                it[revoked] = true
            }
        }
    }

    fun deleteExpired(now: Instant) {
        transaction {
            RefreshTokenTable.deleteWhere { RefreshTokenTable.expiresAt less now }
        }
    }
}
