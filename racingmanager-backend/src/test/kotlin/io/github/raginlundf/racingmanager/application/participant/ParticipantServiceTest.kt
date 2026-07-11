package io.github.raginlundf.racingmanager.application.participant

import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.participant.ParticipantStatus
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SessionRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.util.UUID

class ParticipantServiceTest {

    private val participantRepository = ParticipantRepository()
    private val eventRepository = EventRepository()
    private val auditRepository = AuditRepository()
    private val userRepository = UserRepository()
    private val sessionRepository = SessionRepository()
    private val passwordHasher = PasswordHasher()
    private val authService = AuthService(userRepository, sessionRepository, auditRepository, passwordHasher)
    private val eventService = EventService(eventRepository, ParticipantRepository(), auditRepository)
    private val participantService = ParticipantService(participantRepository, eventRepository, auditRepository)

    private lateinit var actorId: UUID
    private lateinit var eventId: UUID

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        val setupResult = authService.setupAdmin("admin", "password123", "Admin")
        actorId = (setupResult as SetupResult.Success).user.id
        val eventResult = eventService.create("Test Event", null, EventSettings(), actorId)
        val created = eventService.activate((eventResult as CreateEventResult.Success).event.id, 0L, actorId)
        eventId = (created as io.github.raginlundf.racingmanager.application.event.ActivateEventResult.Success).event.id
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    @Test
    fun `create participant returns Success`() {
        val result = participantService.create(eventId, 1, "John", "Doe", null, null, null, actorId)
        val success = assertIs<CreateParticipantResult.Success>(result)
        assertEquals(1, success.participant.startNumber)
        assertEquals("John", success.participant.firstName)
        assertEquals("Doe", success.participant.lastName)
        assertEquals(ParticipantStatus.ACTIVE, success.participant.status)
    }

    @Test
    fun `create participant with duplicate start number returns error`() {
        participantService.create(eventId, 1, "John", "Doe", null, null, null, actorId)
        val result = participantService.create(eventId, 1, "Jane", "Smith", null, null, null, actorId)
        assertIs<CreateParticipantResult.DuplicateStartNumber>(result)
    }

    @Test
    fun `create participant for non-active event returns error`() {
        val draftEvent = eventService.create("Draft", null, EventSettings(), actorId)
        val draftId = (draftEvent as CreateEventResult.Success).event.id
        val result = participantService.create(draftId, 1, "John", "Doe", null, null, null, actorId)
        assertIs<CreateParticipantResult.EventNotActive>(result)
    }

    @Test
    fun `findByEventId returns participants sorted by sortOrder`() {
        participantService.create(eventId, 2, "B", "B", null, null, null, actorId)
        participantService.create(eventId, 1, "A", "A", null, null, null, actorId)

        val participants = participantService.findByEventId(eventId)
        assertEquals(2, participants.size)
    }

    @Test
    fun `findById returns participant`() {
        val created = participantService.create(eventId, 1, "John", "Doe", null, null, null, actorId)
        val id = (created as CreateParticipantResult.Success).participant.id

        val found = participantService.findById(id)
        assertNotNull(found)
        assertEquals("John", found.firstName)
    }

    @Test
    fun `update participant changes fields`() {
        val created = participantService.create(eventId, 1, "John", "Doe", null, null, null, actorId)
        val p = (created as CreateParticipantResult.Success).participant

        val result = participantService.update(p.id, 2, "Jane", "Smith", "Club", actorId)
        val success = assertIs<UpdateParticipantResult.Success>(result)
        assertEquals(2, success.participant.startNumber)
        assertEquals("Jane", success.participant.firstName)
        assertEquals("Smith", success.participant.lastName)
        assertEquals("Club", success.participant.club)
    }

    @Test
    fun `update with duplicate start number returns error`() {
        participantService.create(eventId, 1, "John", "Doe", null, null, null, actorId)
        val created = participantService.create(eventId, 2, "Jane", "Smith", null, null, null, actorId)
        val p = (created as CreateParticipantResult.Success).participant

        val result = participantService.update(p.id, 1, "Jane", "Smith", null, actorId)
        assertIs<UpdateParticipantResult.DuplicateStartNumber>(result)
    }

    @Test
    fun `deactivate changes status to INACTIVE`() {
        val created = participantService.create(eventId, 1, "John", "Doe", null, null, null, actorId)
        val p = (created as CreateParticipantResult.Success).participant

        val result = participantService.deactivate(p.id, actorId)
        val success = assertIs<ParticipantActionResult.Success>(result)
        assertEquals(ParticipantStatus.INACTIVE, success.participant.status)
    }

    @Test
    fun `reactivate changes status to ACTIVE`() {
        val created = participantService.create(eventId, 1, "John", "Doe", null, null, null, actorId)
        val p = (created as CreateParticipantResult.Success).participant
        participantService.deactivate(p.id, actorId)

        val result = participantService.reactivate(p.id, actorId)
        val success = assertIs<ParticipantActionResult.Success>(result)
        assertEquals(ParticipantStatus.ACTIVE, success.participant.status)
    }

    @Test
    fun `randomize assigns sort orders`() {
        participantService.create(eventId, 1, "A", "A", null, null, null, actorId)
        participantService.create(eventId, 2, "B", "B", null, null, null, actorId)
        participantService.create(eventId, 3, "C", "C", null, null, null, actorId)

        val result = participantService.randomize(eventId, actorId)
        assertIs<RandomizeResult.Success>(result)

        val participants = participantService.findByEventId(eventId)
        val orders = participants.mapNotNull { it.sortOrder }
        assertEquals(3, orders.toSet().size)
        assertEquals(listOf(0, 1, 2), orders.sorted())
    }

    @Test
    fun `randomize with same seed produces same order`() {
        participantService.create(eventId, 1, "A", "A", null, null, null, actorId)
        participantService.create(eventId, 2, "B", "B", null, null, null, actorId)
        participantService.create(eventId, 3, "C", "C", null, null, null, actorId)

        participantService.randomize(eventId, actorId)
        val firstOrder = participantService.findByEventId(eventId).map { it.id to it.sortOrder }.toMap()

        participantService.randomize(eventId, actorId, force = true)
        val secondOrder = participantService.findByEventId(eventId).map { it.id to it.sortOrder }.toMap()

        assertEquals(firstOrder, secondOrder)
    }

    @Test
    fun `randomize returns AlreadyRandomized on second call without force`() {
        participantService.create(eventId, 1, "A", "A", null, null, null, actorId)
        participantService.randomize(eventId, actorId)

        val result = participantService.randomize(eventId, actorId)
        assertIs<RandomizeResult.AlreadyRandomized>(result)
    }

    @Test
    fun `importCsv creates participants and reports errors`() {
        val rows = listOf(
            CsvParticipantRow(1, "John", "Doe", null, null, null),
            CsvParticipantRow(2, "Jane", "Smith", "Club", "Car", "Sports"),
            CsvParticipantRow(null, null, null, null, null, null),
        )

        val result = participantService.importCsv(eventId, rows, actorId)
        val completed = assertIs<ImportResult.Completed>(result)
        assertEquals(2, completed.created.size)
        assertEquals(1, completed.errors.size)
    }

    @Test
    fun `importCsv with duplicate start numbers reports errors`() {
        participantService.create(eventId, 1, "Existing", "User", null, null, null, actorId)

        val rows = listOf(
            CsvParticipantRow(1, "John", "Doe", null, null, null),
            CsvParticipantRow(2, "Jane", "Smith", null, null, null),
        )

        val result = participantService.importCsv(eventId, rows, actorId)
        val completed = assertIs<ImportResult.Completed>(result)
        assertEquals(1, completed.created.size)
        assertEquals(1, completed.errors.size)
    }
}
