package io.github.raginlundf.racingmanager.application.event

import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.event.LaneType
import io.github.raginlundf.racingmanager.domain.event.MeasurementType
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SessionRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import io.github.raginlundf.racingmanager.application.auth.AuthService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.util.UUID

class EventServiceTest {

    private val eventRepository = EventRepository()
    private val auditRepository = AuditRepository()
    private val userRepository = UserRepository()
    private val passwordHasher = PasswordHasher()
    private val sessionRepository = SessionRepository()
    private val authService = AuthService(userRepository, sessionRepository, auditRepository, passwordHasher)
    private val eventService = EventService(eventRepository, auditRepository)

    private lateinit var actorId: UUID

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        val result = authService.setupAdmin("admin", "password123", "Admin User")
        actorId = (result as io.github.raginlundf.racingmanager.application.auth.SetupResult.Success).user.id
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    @Test
    fun `create event returns Success with DRAFT status`() {
        val settings = EventSettings()
        val result = eventService.create("Test Event", null, settings, actorId)

        val success = assertIs<CreateEventResult.Success>(result)
        assertEquals("Test Event", success.event.name)
        assertEquals(EventStatus.DRAFT, success.event.status)
        assertEquals(0L, success.event.version)
        assertNotNull(success.event.id)
    }

    @Test
    fun `create event with custom settings`() {
        val settings = EventSettings(
            laneType = LaneType.FOUR_LANE,
            measurementType = MeasurementType.MANUAL,
            maxParticipants = 100,
        )
        val result = eventService.create("Custom Event", "With description", settings, actorId)

        val success = assertIs<CreateEventResult.Success>(result)
        assertEquals(LaneType.FOUR_LANE, success.event.settings.laneType)
        assertEquals(MeasurementType.MANUAL, success.event.settings.measurementType)
        assertEquals(100, success.event.settings.maxParticipants)
        assertEquals("With description", success.event.description)
    }

    @Test
    fun `findById returns created event`() {
        val created = eventService.create("Find Me", null, EventSettings(), actorId)
        val id = (created as CreateEventResult.Success).event.id

        val found = eventService.findById(id)
        assertNotNull(found)
        assertEquals("Find Me", found.name)
    }

    @Test
    fun `findById returns null for unknown event`() {
        val found = eventService.findById(UUID.randomUUID())
        assertNull(found)
    }

    @Test
    fun `findAll returns all events`() {
        eventService.create("Event 1", null, EventSettings(), actorId)
        eventService.create("Event 2", null, EventSettings(), actorId)

        val events = eventService.findAll()
        assertEquals(2, events.size)
    }

    @Test
    fun `update event modifies name and settings`() {
        val created = eventService.create("Original", null, EventSettings(), actorId)
        val event = (created as CreateEventResult.Success).event

        val newSettings = EventSettings(laneType = LaneType.EIGHT_LANE, measurementType = MeasurementType.ELECTRONIC)
        val result = eventService.update(event.id, "Updated", "New desc", newSettings, event.version, actorId)

        val success = assertIs<UpdateEventResult.Success>(result)
        assertEquals("Updated", success.event.name)
        assertEquals("New desc", success.event.description)
        assertEquals(LaneType.EIGHT_LANE, success.event.settings.laneType)
        assertEquals(MeasurementType.ELECTRONIC, success.event.settings.measurementType)
        assertEquals(event.version + 1, success.event.version)
    }

    @Test
    fun `update with wrong version returns Conflict`() {
        val created = eventService.create("Original", null, EventSettings(), actorId)
        val event = (created as CreateEventResult.Success).event

        val result = eventService.update(event.id, "Updated", null, EventSettings(), 999L, actorId)

        val conflict = assertIs<UpdateEventResult.Conflict>(result)
        assertEquals(999L, conflict.expected)
        assertEquals(event.version, conflict.actual)
    }

    @Test
    fun `update unknown event returns NotFound`() {
        val result = eventService.update(UUID.randomUUID(), "Nope", null, EventSettings(), 0L, actorId)

        assertIs<UpdateEventResult.NotFound>(result)
    }

    @Test
    fun `activate event changes status to ACTIVE`() {
        val created = eventService.create("To Activate", null, EventSettings(), actorId)
        val event = (created as CreateEventResult.Success).event

        val result = eventService.activate(event.id, event.version, actorId)

        val success = assertIs<ActivateEventResult.Success>(result)
        assertEquals(EventStatus.ACTIVE, success.event.status)
        assertNotNull(success.event.activatedAt)
    }

    @Test
    fun `activate non-draft event returns InvalidStatus`() {
        val created = eventService.create("To Activate", null, EventSettings(), actorId)
        val event = (created as CreateEventResult.Success).event
        eventService.activate(event.id, event.version, actorId)

        val result = eventService.activate(event.id, 1L, actorId)

        assertIs<ActivateEventResult.InvalidStatus>(result)
    }

    @Test
    fun `archive active event changes status to ARCHIVED`() {
        val created = eventService.create("To Archive", null, EventSettings(), actorId)
        val event = (created as CreateEventResult.Success).event
        eventService.activate(event.id, event.version, actorId)

        val result = eventService.archive(event.id, actorId)

        val success = assertIs<ArchiveEventResult.Success>(result)
        assertEquals(EventStatus.ARCHIVED, success.event.status)
    }

    @Test
    fun `archive non-active event returns InvalidStatus`() {
        val created = eventService.create("Draft Only", null, EventSettings(), actorId)
        val event = (created as CreateEventResult.Success).event

        val result = eventService.archive(event.id, actorId)

        assertIs<ArchiveEventResult.InvalidStatus>(result)
    }

    @Test
    fun `archive unknown event returns NotFound`() {
        val result = eventService.archive(UUID.randomUUID(), actorId)

        assertIs<ArchiveEventResult.NotFound>(result)
    }
}
