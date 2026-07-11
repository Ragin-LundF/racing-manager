package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.user.UserEntity
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.tables.UserTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class UserRepository {

    fun findByUsername(username: String): UserEntity? = transaction {
        UserTable.selectAll().where { UserTable.username eq username }
            .singleOrNull()
            ?.let { row ->
                UserEntity(
                    id = row[UserTable.id],
                    username = row[UserTable.username],
                    passwordHash = row[UserTable.passwordHash],
                    displayName = row[UserTable.displayName],
                    role = UserRole.valueOf(row[UserTable.role]),
                    createdAt = row[UserTable.createdAt],
                    updatedAt = row[UserTable.updatedAt],
                )
            }
    }

    fun findById(id: UUID): UserEntity? = transaction {
        UserTable.selectAll().where { UserTable.id eq id }
            .singleOrNull()
            ?.let { row ->
                UserEntity(
                    id = row[UserTable.id],
                    username = row[UserTable.username],
                    passwordHash = row[UserTable.passwordHash],
                    displayName = row[UserTable.displayName],
                    role = UserRole.valueOf(row[UserTable.role]),
                    createdAt = row[UserTable.createdAt],
                    updatedAt = row[UserTable.updatedAt],
                )
            }
    }

    fun count(): Long = transaction {
        UserTable.selectAll().count()
    }

    fun insert(user: UserEntity) = transaction {
        UserTable.insert {
            it[id] = user.id
            it[username] = user.username
            it[passwordHash] = user.passwordHash
            it[displayName] = user.displayName
            it[role] = user.role.name
            it[createdAt] = user.createdAt
            it[updatedAt] = user.updatedAt
        }
    }

    fun deleteAll() = transaction {
        UserTable.deleteAll()
    }
}
