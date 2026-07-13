package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.user.UserEntity
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.tables.UserTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class UserRepository {

    /** Username is unique only within a tenant (not globally), so more than one
        row can share a username across different tenants. Use
        [findByTenantAndUsername] when the tenant is known (e.g. login with a
        tenant slug); this is only safe to call when the caller has already
        confirmed the result is unambiguous (see [findAllByUsername]). */
    fun findByUsername(username: String): UserEntity? {
        return transaction {
            UserTable.selectAll().where { UserTable.username eq username }
                .singleOrNull()
                ?.toUserEntity()
        }
    }

    /** All users sharing [username] across every tenant — used to detect and
        safely reject ambiguous logins rather than guessing which account was
        meant. */
    fun findAllByUsername(username: String): List<UserEntity> {
        return transaction {
            UserTable.selectAll().where { UserTable.username eq username }
                .map { it.toUserEntity() }
        }
    }

    fun findByTenantAndUsername(tenantId: UUID, username: String): UserEntity? {
        return transaction {
            UserTable.selectAll().where { (UserTable.tenantId eq tenantId) and (UserTable.username eq username) }
                .singleOrNull()
                ?.toUserEntity()
        }
    }

    fun findById(id: UUID): UserEntity? {
        return transaction {
            UserTable.selectAll().where { UserTable.id eq id }
                .singleOrNull()
                ?.toUserEntity()
        }
    }

    fun count(): Long {
        return transaction {
            UserTable.selectAll().count()
        }
    }

    fun insert(user: UserEntity) {
        transaction {
            UserTable.insert {
                it[id] = user.id
                it[tenantId] = user.tenantId
                it[username] = user.username
                it[passwordHash] = user.passwordHash
                it[displayName] = user.displayName
                it[email] = user.email
                it[role] = user.role.name
                it[tokenVersion] = user.tokenVersion
                it[createdAt] = user.createdAt
                it[updatedAt] = user.updatedAt
            }
        }
    }

    fun updatePassword(id: UUID, newHash: String) {
        transaction {
            UserTable.update({ UserTable.id eq id }) {
                it[passwordHash] = newHash
            }
        }
    }

    fun updateRole(id: UUID, role: UserRole) {
        transaction {
            UserTable.update({ UserTable.id eq id }) {
                it[UserTable.role] = role.name
            }
        }
    }

    fun findByTenantId(tenantId: UUID): List<UserEntity> {
        return transaction {
            UserTable.selectAll().where { UserTable.tenantId eq tenantId }
                .map { it.toUserEntity() }
        }
    }

    /** Invalidates every outstanding refresh token for this user ("logout
        everywhere") without needing to enumerate and delete them. */
    fun incrementTokenVersion(id: UUID) {
        transaction {
            val current = UserTable.selectAll().where { UserTable.id eq id }.single()[UserTable.tokenVersion]
            UserTable.update({ UserTable.id eq id }) {
                it[tokenVersion] = current + 1
            }
        }
    }

    fun deleteAll() {
        transaction {
            UserTable.deleteAll()
        }
    }

    private fun ResultRow.toUserEntity(): UserEntity {
        return UserEntity(
            id = this[UserTable.id],
            tenantId = this[UserTable.tenantId],
            username = this[UserTable.username],
            passwordHash = this[UserTable.passwordHash],
            displayName = this[UserTable.displayName],
            email = this[UserTable.email],
            role = UserRole.valueOf(this[UserTable.role]),
            createdAt = this[UserTable.createdAt],
            updatedAt = this[UserTable.updatedAt],
            tokenVersion = this[UserTable.tokenVersion],
        )
    }
}
