package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.domain.tenant.TenantEntity
import io.github.raginlundf.racingmanager.domain.user.UserEntity
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

/** `audit_entries` has no `tenant_id` column of its own — tenant filtering
goes through a join on the entry's actor. Verifies that join actually
isolates tenants at the DB layer, not just compiles. */
class AuditRepositoryTenantTest {

    private val auditRepository = AuditRepository()
    private val tenantRepository = TenantRepository()
    private val userRepository = UserRepository()

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    private fun createUser(tenantId: UUID, username: String): UserEntity {
        val user = UserEntity(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            username = username,
            passwordHash = "hash",
            displayName = username,
            role = UserRole.ADMIN,
            createdAt = Clock.System.now(),
        )
        userRepository.insert(user = user)
        return user
    }

    @Test
    fun `query with a tenantId only returns entries whose actor belongs to that tenant`() {
        val tenantA = TenantEntity(
            id = UUID.randomUUID(),
            slug = "a",
            displayName = "A",
            createdAt = Clock.System.now()
        )
        val tenantB = TenantEntity(
            id = UUID.randomUUID(),
            slug = "b",
            displayName = "B",
            createdAt = Clock.System.now()
        )
        tenantRepository.insert(tenant = tenantA)
        tenantRepository.insert(tenant = tenantB)
        val userA = createUser(tenantId = tenantA.id, username = "user-a")
        val userB = createUser(tenantId = tenantB.id, username = "user-b")

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = userA.id,
                action = "EVENT_CREATED",
                occurredAt = Clock.System.now()
            ),
        )
        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = userB.id,
                action = "EVENT_CREATED",
                occurredAt = Clock.System.now()
            ),
        )

        val entriesForA = auditRepository.query(tenantId = tenantA.id)

        assertEquals(expected = 1, actual = entriesForA.size)
        assertEquals(expected = userA.id, actual = entriesForA.single().actorId)
    }

    @Test
    fun `query without a tenantId returns all entries`() {
        val tenantA = TenantEntity(
            id = UUID.randomUUID(),
            slug = "a",
            displayName = "A",
            createdAt = Clock.System.now()
        )
        tenantRepository.insert(tenant = tenantA)
        val userA = createUser(tenantId = tenantA.id, username = "user-a")

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = userA.id,
                action = "EVENT_CREATED",
                occurredAt = Clock.System.now()
            ),
        )

        assertEquals(expected = 1, actual = auditRepository.query().size)
    }
}
