package io.github.raginlundf.racingmanager.application.event

import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.event.LaneType
import io.github.raginlundf.racingmanager.domain.event.MeasurementType
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.MembershipRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.RefreshTokenRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.TenantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.security.LocalJwtKeyProvider
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
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
    private val jwtKeyProvider = LocalJwtKeyProvider(SigningKeyRepository())
    private val jwtService = JwtService(jwtKeyProvider)
    private val authService = AuthService(
        userRepository,
        TenantRepository(),
        MembershipRepository(),
        RefreshTokenRepository(),
        auditRepository,
        passwordHasher,
        jwtService,
    )
    private val eventService = EventService(eventRepository, ParticipantRepository(), auditRepository)

    private lateinit var actorId: UUID
    private lateinit var tenantId: UUID

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        jwtKeyProvider.ensureKeyExists()
        val result = authService.setupAdmin("admin", "password123", "Admin User")
        val user = (result as io.github.raginlundf.racingmanager.application.auth.SetupResult.Success).user
        actorId = user.id
        tenantId = user.tenantId
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    @Test
    fun `create event returns Success and starts it as the active event`() {
        val settings = EventSettings()
        val result = eventService.create("Test Event", null, settings, actorId, tenantId)

        val success = assertIs<CreateEventResult.Success>(result)
        assertEquals("Test Event", success.event.name)
        assertEquals(EventStatus.ACTIVE, success.event.status)
        assertNotNull(success.event.activatedAt)
        assertEquals(0L, success.event.version)
        assertNotNull(success.event.id)
    }

    @Test
    fun `creating an event returns the previously active event to DRAFT`() {
        val first = (eventService.create("First", null, EventSettings(), actorId, tenantId)
            as CreateEventResult.Success).event

        val second = (eventService.create("Second", null, EventSettings(), actorId, tenantId)
            as CreateEventResult.Success).event

        val standDown = eventRepository.findById(first.id)!!
        assertEquals(EventStatus.DRAFT, standDown.status)
        assertNull(standDown.activatedAt)
        assertEquals(EventStatus.ACTIVE, eventRepository.findById(second.id)!!.status)
    }

    @Test
    fun `create event with custom settings`() {
        val settings = EventSettings(
            laneType = LaneType.FOUR_LANE,
            measurementType = MeasurementType.MANUAL,
            maxParticipants = 100,
        )
        val result = eventService.create("Custom Event", "With description", settings, actorId, tenantId)

        val success = assertIs<CreateEventResult.Success>(result)
        assertEquals(LaneType.FOUR_LANE, success.event.settings.laneType)
        assertEquals(MeasurementType.MANUAL, success.event.settings.measurementType)
        assertEquals(100, success.event.settings.maxParticipants)
        assertEquals("With description", success.event.description)
    }

    @Test
    fun `delete removes the event`() {
        val created = eventService.create("Doomed", null, EventSettings(), actorId, tenantId)
        val id = (created as CreateEventResult.Success).event.id

        val result = eventService.delete(id, actorId)

        assertIs<DeleteEventResult.Success>(result)
        assertNull(eventService.findById(id))
    }

    @Test
    fun `delete unknown event returns NotFound`() {
        assertIs<DeleteEventResult.NotFound>(eventService.delete(UUID.randomUUID(), actorId))
    }

    @Test
    fun `findById returns created event`() {
        val created = eventService.create("Find Me", null, EventSettings(), actorId, tenantId)
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
        eventService.create("Event 1", null, EventSettings(), actorId, tenantId)
        eventService.create("Event 2", null, EventSettings(), actorId, tenantId)

        val events = eventService.findAll()
        assertEquals(2, events.size)
    }

    @Test
    fun `update event modifies name and settings`() {
        val created = eventService.create("Original", null, EventSettings(), actorId, tenantId)
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
        val created = eventService.create("Original", null, EventSettings(), actorId, tenantId)
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
        val created = eventService.create("To Activate", null, EventSettings(), actorId, tenantId)
        // A second event stands the first one down to DRAFT, so it can be activated again.
        eventService.create("Takes Over", null, EventSettings(), actorId, tenantId)
        val event = eventRepository.findById((created as CreateEventResult.Success).event.id)!!

        val result = eventService.activate(event.id, event.version, actorId)

        val success = assertIs<ActivateEventResult.Success>(result)
        assertEquals(EventStatus.ACTIVE, success.event.status)
        assertNotNull(success.event.activatedAt)
    }

    @Test
    fun `activate non-draft event returns InvalidStatus`() {
        val created = eventService.create("To Activate", null, EventSettings(), actorId, tenantId)
        val event = (created as CreateEventResult.Success).event

        // A new event is already ACTIVE — activating it again is invalid.
        val result = eventService.activate(event.id, event.version, actorId)

        assertIs<ActivateEventResult.InvalidStatus>(result)
    }

    @Test
    fun `archive active event changes status to ARCHIVED`() {
        val created = eventService.create("To Archive", null, EventSettings(), actorId, tenantId)
        val event = (created as CreateEventResult.Success).event

        val result = eventService.archive(event.id, actorId)

        val success = assertIs<ArchiveEventResult.Success>(result)
        assertEquals(EventStatus.ARCHIVED, success.event.status)
    }

    @Test
    fun `archive non-active event returns InvalidStatus`() {
        val created = eventService.create("Draft Only", null, EventSettings(), actorId, tenantId)
        // A second event returns the first to DRAFT.
        eventService.create("Takes Over", null, EventSettings(), actorId, tenantId)
        val event = (created as CreateEventResult.Success).event

        val result = eventService.archive(event.id, actorId)

        assertIs<ArchiveEventResult.InvalidStatus>(result)
    }

    @Test
    fun `archive unknown event returns NotFound`() {
        val result = eventService.archive(UUID.randomUUID(), actorId)

        assertIs<ArchiveEventResult.NotFound>(result)
    }

    @Test
    fun `reactivate archived event changes status to ACTIVE`() {
        val created = eventService.create("To Reactivate", null, EventSettings(), actorId, tenantId)
        val event = (created as CreateEventResult.Success).event
        eventService.activate(event.id, event.version, actorId)
        eventService.archive(event.id, actorId)

        val result = eventService.reactivate(event.id, actorId)

        val success = assertIs<ReactivateEventResult.Success>(result)
        assertEquals(EventStatus.ACTIVE, success.event.status)
    }

    @Test
    fun `reactivate non-archived event returns InvalidStatus`() {
        val created = eventService.create("Draft Only", null, EventSettings(), actorId, tenantId)
        val event = (created as CreateEventResult.Success).event

        val result = eventService.reactivate(event.id, actorId)

        assertIs<ReactivateEventResult.InvalidStatus>(result)
    }

    @Test
    fun `update is allowed while the event is ACTIVE`() {
        val event = (eventService.create("Running", null, EventSettings(), actorId, tenantId)
            as CreateEventResult.Success).event
        assertEquals(EventStatus.ACTIVE, event.status)

        val result = eventService.update(
            id = event.id,
            name = "Renamed",
            description = null,
            settings = EventSettings(trackLength = 120),
            expectedVersion = event.version,
            actorId = actorId,
        )

        val success = assertIs<UpdateEventResult.Success>(result)
        assertEquals("Renamed", success.event.name)
        assertEquals(120, success.event.settings.trackLength)
    }

    @Test
    fun `update of an archived event returns CannotModifyFinishedEvent`() {
        val event = (eventService.create("To Archive", null, EventSettings(), actorId, tenantId)
            as CreateEventResult.Success).event
        eventService.archive(event.id, actorId)

        val result = eventService.update(
            id = event.id,
            name = "Renamed",
            description = null,
            settings = EventSettings(),
            expectedVersion = event.version + 1,
            actorId = actorId,
        )

        assertIs<UpdateEventResult.CannotModifyFinishedEvent>(result)
    }

    @Test
    fun `reactivate unknown event returns NotFound`() {
        val result = eventService.reactivate(UUID.randomUUID(), actorId)

        assertIs<ReactivateEventResult.NotFound>(result)
    }
}
