package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.tenant.TenantEntity
import io.github.raginlundf.racingmanager.domain.tenant.TenantStatus
import io.github.raginlundf.racingmanager.infrastructure.tables.AuditEntryTable
import io.github.raginlundf.racingmanager.infrastructure.tables.EventSeedTable
import io.github.raginlundf.racingmanager.infrastructure.tables.EventTable
import io.github.raginlundf.racingmanager.infrastructure.tables.HeatLaneTable
import io.github.raginlundf.racingmanager.infrastructure.tables.HeatTable
import io.github.raginlundf.racingmanager.infrastructure.tables.KnockoutMatchTable
import io.github.raginlundf.racingmanager.infrastructure.tables.KnockoutTournamentTable
import io.github.raginlundf.racingmanager.infrastructure.tables.MeasurementTable
import io.github.raginlundf.racingmanager.infrastructure.tables.MembershipTable
import io.github.raginlundf.racingmanager.infrastructure.tables.PairedInstanceTable
import io.github.raginlundf.racingmanager.infrastructure.tables.PairingCodeTable
import io.github.raginlundf.racingmanager.infrastructure.tables.ParticipantTable
import io.github.raginlundf.racingmanager.infrastructure.tables.QualificationTable
import io.github.raginlundf.racingmanager.infrastructure.tables.RefreshTokenTable
import io.github.raginlundf.racingmanager.infrastructure.tables.SpectatorExchangeCodeTable
import io.github.raginlundf.racingmanager.infrastructure.tables.SyncedResultTable
import io.github.raginlundf.racingmanager.infrastructure.tables.TenantTable
import io.github.raginlundf.racingmanager.infrastructure.tables.UserTable
import io.github.raginlundf.racingmanager.infrastructure.tables.VehicleTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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

    fun findByStatus(status: TenantStatus): List<TenantEntity> {
        return transaction {
            TenantTable.selectAll().where { TenantTable.status eq status.name }
                .map { it.toTenantEntity() }
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

    /** Hard-deletes a tenant and every row that belongs to it, in one transaction.
        Deletes children before parents because SQLite does not enforce foreign keys
        at runtime (no `PRAGMA foreign_keys=ON`) yet MariaDB does — explicit ordering
        is correct on both. Audit entries authored by the tenant's own users are
        removed (matching how [AuditRepository.query] scopes a tenant's audit trail
        via the actor→user join); supervisor-authored lifecycle entries live in the
        platform tenant and are intentionally left intact. */
    fun purgeTenant(id: UUID) {
        transaction {
            val eventIds = EventTable.selectAll().where { EventTable.tenantId eq id }
                .map { it[EventTable.id] }
            val userIds = UserTable.selectAll().where { UserTable.tenantId eq id }
                .map { it[UserTable.id] }
            val heatIds = HeatTable.selectAll().where { HeatTable.eventId inList eventIds }
                .map { it[HeatTable.id] }
            val tournamentIds = KnockoutTournamentTable.selectAll()
                .where { KnockoutTournamentTable.eventId inList eventIds }
                .map { it[KnockoutTournamentTable.id] }
            val participantIds = ParticipantTable.selectAll()
                .where { ParticipantTable.eventId inList eventIds }
                .map { it[ParticipantTable.id] }

            MeasurementTable.deleteWhere { heatId inList heatIds }
            HeatLaneTable.deleteWhere { heatId inList heatIds }
            VehicleTable.deleteWhere { participantId inList participantIds }
            KnockoutMatchTable.deleteWhere { this.tournamentId inList tournamentIds }
            KnockoutTournamentTable.deleteWhere { eventId inList eventIds }
            QualificationTable.deleteWhere { eventId inList eventIds }
            HeatTable.deleteWhere { eventId inList eventIds }
            ParticipantTable.deleteWhere { eventId inList eventIds }
            EventSeedTable.deleteWhere { eventId inList eventIds }
            SpectatorExchangeCodeTable.deleteWhere { tenantId eq id }
            SyncedResultTable.deleteWhere { tenantId eq id }
            EventTable.deleteWhere { tenantId eq id }
            PairingCodeTable.deleteWhere { tenantId eq id }
            PairedInstanceTable.deleteWhere { tenantId eq id }
            RefreshTokenTable.deleteWhere { tenantId eq id }
            MembershipTable.deleteWhere { tenantId eq id }
            AuditEntryTable.deleteWhere { actorId inList userIds }
            UserTable.deleteWhere { tenantId eq id }
            TenantTable.deleteWhere { TenantTable.id eq id }
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
