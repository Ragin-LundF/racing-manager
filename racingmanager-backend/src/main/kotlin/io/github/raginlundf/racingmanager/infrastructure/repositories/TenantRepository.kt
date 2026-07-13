package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.tenant.TenantEntity
import io.github.raginlundf.racingmanager.domain.tenant.TenantStatus
import io.github.raginlundf.racingmanager.infrastructure.tables.TenantTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class TenantRepository {

    fun findById(id: UUID): TenantEntity? {
        return transaction {
            TenantTable.selectAll().where { TenantTable.id eq id }
                .singleOrNull()
                ?.toTenantEntity()
        }
    }

    fun findBySlug(slug: String): TenantEntity? {
        return transaction {
            TenantTable.selectAll().where { TenantTable.slug eq slug }
                .singleOrNull()
                ?.toTenantEntity()
        }
    }

    fun findAll(): List<TenantEntity> {
        return transaction {
            TenantTable.selectAll().map { it.toTenantEntity() }
        }
    }

    fun insert(tenant: TenantEntity) {
        transaction {
            TenantTable.insert {
                it[id] = tenant.id
                it[slug] = tenant.slug
                it[displayName] = tenant.displayName
                it[status] = tenant.status.name
                it[settings] = tenant.settings
                it[createdAt] = tenant.createdAt
                it[updatedAt] = tenant.updatedAt
            }
        }
    }

    fun update(tenant: TenantEntity) {
        transaction {
            TenantTable.update({ TenantTable.id eq tenant.id }) {
                it[displayName] = tenant.displayName
                it[status] = tenant.status.name
                it[settings] = tenant.settings
                it[updatedAt] = tenant.updatedAt
            }
        }
    }

    fun deleteAll() {
        transaction {
            TenantTable.deleteAll()
        }
    }

    private fun ResultRow.toTenantEntity(): TenantEntity {
        return TenantEntity(
            id = this[TenantTable.id],
            slug = this[TenantTable.slug],
            displayName = this[TenantTable.displayName],
            status = TenantStatus.valueOf(this[TenantTable.status]),
            settings = this[TenantTable.settings],
            createdAt = this[TenantTable.createdAt],
            updatedAt = this[TenantTable.updatedAt],
        )
    }
}
