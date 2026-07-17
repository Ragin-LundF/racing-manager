package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.tenant.TenantStatus
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.tables.AuditEntryTable
import io.github.raginlundf.racingmanager.infrastructure.tables.EventTable
import io.github.raginlundf.racingmanager.infrastructure.tables.HeatTable
import io.github.raginlundf.racingmanager.infrastructure.tables.MeasurementTable
import io.github.raginlundf.racingmanager.infrastructure.tables.MembershipTable
import io.github.raginlundf.racingmanager.infrastructure.tables.TenantTable
import io.github.raginlundf.racingmanager.infrastructure.tables.UserTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

/** Verifies [TenantRepository.purgeTenant] hard-deletes a tenant together with its
    dependent rows (events → heats → measurements, memberships, users, and the tenant's
    own audit entries) while leaving another tenant's data untouched. */
class TenantRepositoryPurgeTest {

    private val tenantRepository = TenantRepository()

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    /** Inserts a tenant with a user, an event, a heat, a measurement, a membership,
        and an audit entry authored by that user. Returns the tenant id. */
    private fun seedTenant(slug: String): UUID {
        val now = Clock.System.now()
        val tenantId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        transaction {
            TenantTable.insert {
                it[TenantTable.id] = tenantId
                it[TenantTable.slug] = slug
                it[displayName] = slug
                it[status] = TenantStatus.PENDING_DELETION.name
                it[createdAt] = now
                it[updatedAt] = now
            }
            UserTable.insert {
                it[UserTable.id] = userId
                it[UserTable.tenantId] = tenantId
                it[username] = "user-$slug"
                it[passwordHash] = "hash"
                it[displayName] = "User"
                it[role] = "ADMIN"
                it[createdAt] = now
            }
            MembershipTable.insert {
                it[MembershipTable.id] = UUID.randomUUID()
                it[MembershipTable.userId] = userId
                it[MembershipTable.tenantId] = tenantId
                it[status] = "ACTIVE"
                it[role] = "ADMIN"
                it[createdAt] = now
            }
            AuditEntryTable.insert {
                it[AuditEntryTable.id] = UUID.randomUUID()
                it[actorId] = userId
                it[action] = "SOMETHING"
                it[occurredAt] = now
            }
            seedEventGraph(tenantId = tenantId, userId = userId, now = now)
        }
        return tenantId
    }

    /** Inserts an event → heat → measurement chain for the tenant (runs inside the
        caller's transaction). */
    private fun seedEventGraph(tenantId: UUID, userId: UUID, now: kotlin.time.Instant) {
        val eventId = UUID.randomUUID()
        val heatId = UUID.randomUUID()
        EventTable.insert {
            it[EventTable.id] = eventId
            it[EventTable.tenantId] = tenantId
            it[name] = "Event"
            it[status] = "DRAFT"
            it[laneType] = "SINGLE"
            it[measurementType] = "TIME"
            it[version] = 1
            it[createdBy] = userId
            it[createdAt] = now
        }
        HeatTable.insert {
            it[HeatTable.id] = heatId
            it[HeatTable.eventId] = eventId
            it[round] = 1
            it[heatNumber] = 1
            it[status] = "PENDING"
            it[createdAt] = now
        }
        MeasurementTable.insert {
            it[MeasurementTable.id] = UUID.randomUUID()
            it[MeasurementTable.heatId] = heatId
            it[lane] = 1
            it[durationNanos] = 1_000L
            it[outcome] = "FINISHED"
            it[receivedAt] = now
        }
    }

    private fun rowCountsForTenant(tenantId: UUID): Long = transaction {
        val userIds = UserTable.selectAll().where { UserTable.tenantId eq tenantId }
            .map { it[UserTable.id] }
        val eventIds = EventTable.selectAll().where { EventTable.tenantId eq tenantId }
            .map { it[EventTable.id] }
        val heatIds = HeatTable.selectAll().where { HeatTable.eventId inList eventIds }
            .map { it[HeatTable.id] }
        TenantTable.selectAll().where { TenantTable.id eq tenantId }.count() +
            UserTable.selectAll().where { UserTable.tenantId eq tenantId }.count() +
            EventTable.selectAll().where { EventTable.tenantId eq tenantId }.count() +
            HeatTable.selectAll().where { HeatTable.eventId inList eventIds }.count() +
            MeasurementTable.selectAll().where { MeasurementTable.heatId inList heatIds }.count() +
            MembershipTable.selectAll().where { MembershipTable.tenantId eq tenantId }.count() +
            AuditEntryTable.selectAll().where { AuditEntryTable.actorId inList userIds }.count()
    }

    @Test
    fun `purgeTenant removes the tenant and all its dependent rows`() {
        val tenantId = seedTenant(slug = "doomed")
        require(rowCountsForTenant(tenantId) == 7L) { "seed failed" }

        tenantRepository.purgeTenant(tenantId)

        assertEquals(expected = 0L, actual = rowCountsForTenant(tenantId))
    }

    @Test
    fun `purgeTenant leaves another tenant's data untouched`() {
        val doomed = seedTenant(slug = "doomed")
        val survivor = seedTenant(slug = "survivor")

        tenantRepository.purgeTenant(doomed)

        assertEquals(expected = 7L, actual = rowCountsForTenant(survivor))
    }
}
