package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.tenant.MembershipEntity
import io.github.raginlundf.racingmanager.domain.tenant.MembershipStatus
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.tables.MembershipTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class MembershipRepository {

    fun findByUserId(userId: UUID): List<MembershipEntity> {
        return transaction {
            MembershipTable.selectAll().where { MembershipTable.userId eq userId }
                .map { it.toMembershipEntity() }
        }
    }

    fun findByUserAndTenant(userId: UUID, tenantId: UUID): MembershipEntity? {
        return transaction {
            MembershipTable.selectAll()
                .where { (MembershipTable.userId eq userId) and (MembershipTable.tenantId eq tenantId) }
                .singleOrNull()
                ?.toMembershipEntity()
        }
    }

    fun findByTenantId(tenantId: UUID): List<MembershipEntity> {
        return transaction {
            MembershipTable.selectAll().where { MembershipTable.tenantId eq tenantId }
                .map { it.toMembershipEntity() }
        }
    }

    fun updateRoleAndStatus(userId: UUID, tenantId: UUID, role: UserRole, status: MembershipStatus, updatedAt: kotlin.time.Instant) {
        transaction {
            MembershipTable.update(where = { (MembershipTable.userId eq userId) and (MembershipTable.tenantId eq tenantId) }) {
                it[MembershipTable.role] = role.name
                it[MembershipTable.status] = status.name
                it[MembershipTable.updatedAt] = updatedAt
            }
        }
    }

    fun insert(membership: MembershipEntity) {
        transaction {
            MembershipTable.insert {
                it[id] = membership.id
                it[userId] = membership.userId
                it[tenantId] = membership.tenantId
                it[status] = membership.status.name
                it[role] = membership.role.name
                it[createdAt] = membership.createdAt
                it[updatedAt] = membership.updatedAt
            }
        }
    }

    fun deleteAll() {
        transaction {
            MembershipTable.deleteAll()
        }
    }

    private fun ResultRow.toMembershipEntity(): MembershipEntity {
        return MembershipEntity(
            id = this[MembershipTable.id],
            userId = this[MembershipTable.userId],
            tenantId = this[MembershipTable.tenantId],
            status = MembershipStatus.valueOf(this[MembershipTable.status]),
            role = UserRole.valueOf(this[MembershipTable.role]),
            createdAt = this[MembershipTable.createdAt],
            updatedAt = this[MembershipTable.updatedAt],
        )
    }
}
