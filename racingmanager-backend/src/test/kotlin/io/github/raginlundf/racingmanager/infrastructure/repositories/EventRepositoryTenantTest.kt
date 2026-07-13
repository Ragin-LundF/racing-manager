package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.tenant.TenantEntity
import io.github.raginlundf.racingmanager.domain.user.UserEntity
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import java.util.UUID

/** Verifies the defense-in-depth guarantee from the tenant design doc: a
    repository-level tenant filter must refuse to return another tenant's
    event even when the caller supplies a valid event id — a missed
    route-level authorization check must not be enough to leak data. */
class EventRepositoryTenantTest {

    private val eventRepository = EventRepository()
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

    private fun createTenant(slug: String): TenantEntity {
        val tenant = TenantEntity(id = UUID.randomUUID(), slug = slug, displayName = slug, createdAt = Clock.System.now())
        tenantRepository.insert(tenant)
        return tenant
    }

    private fun createEventForTenant(tenantId: UUID): EventEntity {
        val creator = UserEntity(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            username = "actor-${UUID.randomUUID()}",
            passwordHash = "hash",
            displayName = "Actor",
            role = UserRole.ADMIN,
            createdAt = Clock.System.now(),
        )
        userRepository.insert(creator)
        val event = EventEntity(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            name = "Event",
            status = EventStatus.DRAFT,
            settings = EventSettings(),
            createdBy = creator.id,
            createdAt = Clock.System.now(),
        )
        eventRepository.insert(event)
        return event
    }

    @Test
    fun `findByIdForTenant returns the event when it belongs to the tenant`() {
        val tenantA = createTenant("tenant-a")
        val event = createEventForTenant(tenantA.id)

        val found = eventRepository.findByIdForTenant(event.id, tenantA.id)

        assertEquals(expected = event.id, actual = found?.id)
    }

    @Test
    fun `findByIdForTenant refuses to return another tenant's event`() {
        val tenantA = createTenant("tenant-a")
        val tenantB = createTenant("tenant-b")
        val eventOfA = createEventForTenant(tenantA.id)

        val leaked = eventRepository.findByIdForTenant(eventOfA.id, tenantB.id)

        assertNull(leaked)
    }

    @Test
    fun `findAllForTenant only returns events owned by that tenant`() {
        val tenantA = createTenant("tenant-a")
        val tenantB = createTenant("tenant-b")
        createEventForTenant(tenantA.id)
        createEventForTenant(tenantA.id)
        createEventForTenant(tenantB.id)

        val eventsOfA = eventRepository.findAllForTenant(tenantA.id)

        assertEquals(expected = 2, actual = eventsOfA.size)
    }
}
