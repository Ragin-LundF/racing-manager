package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.participant.CreateParticipantResult
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.gateway.SimulationMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SessionRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.util.UUID

class HeatServiceTest {

    private val eventRepository = EventRepository()
    private val auditRepository = AuditRepository()
    private val userRepository = UserRepository()
    private val passwordHasher = PasswordHasher()
    private val sessionRepository = SessionRepository()
    private val participantRepository = ParticipantRepository()
    private val heatRepository = HeatRepository()
    private val authService = AuthService(userRepository, sessionRepository, auditRepository, passwordHasher)
    private val eventService = EventService(eventRepository, ParticipantRepository(), auditRepository)
    private val participantService = ParticipantService(participantRepository, eventRepository, auditRepository)
    private val measurementGateway = SimulationMeasurementGateway()
    private val heatService = HeatService(heatRepository, eventRepository, participantRepository, auditRepository, measurementGateway)

    private lateinit var actorId: UUID
    private lateinit var eventId: UUID
    private lateinit var participantId1: UUID
    private lateinit var participantId2: UUID

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        val result = authService.setupAdmin("admin", "password123", "Admin User")
        actorId = (result as SetupResult.Success).user.id

        val created = eventService.create("Test Event", null, EventSettings(), actorId)
        val event = (created as CreateEventResult.Success).event
        eventService.activate(event.id, event.version, actorId)
        eventId = event.id

        val p1 = participantService.create(eventId, 1, "Alice", "Smith", null, null, null, actorId)
        participantId1 = (p1 as CreateParticipantResult.Success).participant.id
        val p2 = participantService.create(eventId, 2, "Bob", "Jones", null, null, null, actorId)
        participantId2 = (p2 as CreateParticipantResult.Success).participant.id
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    @Test
    fun `create heat returns Success`() {
        val result = heatService.create(eventId, listOf(participantId1, participantId2), actorId)

        val success = assertIs<CreateHeatResult.Success>(result)
        assertEquals(HeatStatus.PLANNED, success.heat.status)
        assertEquals(2, success.heat.lanes.size)
        assertEquals(1, success.heat.round)
        assertEquals(1, success.heat.heatNumber)
    }

    @Test
    fun `create heat for non-active event returns EventNotActive`() {
        val draftEvent = eventService.create("Draft", null, EventSettings(), actorId)
        val draftId = (draftEvent as CreateEventResult.Success).event.id

        val result = heatService.create(draftId, listOf(participantId1), actorId)

        assertIs<CreateHeatResult.EventNotActive>(result)
    }

    @Test
    fun `create heat with unknown participant returns ParticipantNotFound`() {
        val result = heatService.create(eventId, listOf(UUID.randomUUID()), actorId)

        assertIs<CreateHeatResult.ParticipantNotFound>(result)
    }

    @Test
    fun `findById returns created heat`() {
        val created = heatService.create(eventId, listOf(participantId1, participantId2), actorId)
        val heatId = (created as CreateHeatResult.Success).heat.id

        val found = heatService.findById(heatId)
        assertNotNull(found)
        assertEquals(heatId, found.id)
    }

    @Test
    fun `findById returns null for unknown heat`() {
        val found = heatService.findById(UUID.randomUUID())
        assertNull(found)
    }

    @Test
    fun `findByEventId returns all heats`() {
        heatService.create(eventId, listOf(participantId1, participantId2), actorId)
        heatService.create(eventId, listOf(participantId1, participantId2), actorId)

        val heats = heatService.findByEventId(eventId)
        assertEquals(2, heats.size)
    }

    @Test
    fun `findLatestByEventId returns most recent heat`() {
        heatService.create(eventId, listOf(participantId1, participantId2), actorId)
        val second = heatService.create(eventId, listOf(participantId1, participantId2), actorId)
        val secondId = (second as CreateHeatResult.Success).heat.id

        val latest = heatService.findLatestByEventId(eventId)
        assertNotNull(latest)
        assertEquals(eventId, latest.eventId)
        assertEquals(2, latest.heatNumber)
    }

    @Test
    fun `arm changes status to ARMED`() = runBlocking {
        val created = heatService.create(eventId, listOf(participantId1, participantId2), actorId)
        val heatId = (created as CreateHeatResult.Success).heat.id

        val result = heatService.arm(heatId, actorId)

        val success = assertIs<ArmHeatResult.Success>(result)
        assertEquals(HeatStatus.ARMED, success.heat.status)
    }

    @Test
    fun `arm unknown heat returns NotFound`() = runBlocking {
        val result = heatService.arm(UUID.randomUUID(), actorId)
        assertIs<ArmHeatResult.NotFound>(result)
    }

    @Test
    fun `cancel armed heat changes status to CANCELLED`() = runBlocking {
        val created = heatService.create(eventId, listOf(participantId1, participantId2), actorId)
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(heatId, actorId)

        val result = heatService.cancel(heatId, actorId)

        val success = assertIs<CancelHeatResult.Success>(result)
        assertEquals(HeatStatus.CANCELLED, success.heat.status)
    }

    @Test
    fun `repeat resets status to PLANNED`() = runBlocking {
        val created = heatService.create(eventId, listOf(participantId1, participantId2), actorId)
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(heatId, actorId)

        val result = heatService.repeat(heatId, actorId)

        val success = assertIs<RepeatHeatResult.Success>(result)
        assertEquals(HeatStatus.PLANNED, success.heat.status)
    }

    @Test
    fun `accept result returns Success`() = runBlocking {
        val created = heatService.create(eventId, listOf(participantId1, participantId2), actorId)
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(heatId, actorId)
        heatService.start(heatId, actorId)
        heatService.finish(heatId, actorId)

        val result = heatService.acceptResult(heatId, actorId)
        assertIs<AcceptResult.Success>(result)
    }

    @Test
    fun `reject result returns Success`() = runBlocking {
        val created = heatService.create(eventId, listOf(participantId1, participantId2), actorId)
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(heatId, actorId)
        heatService.start(heatId, actorId)
        heatService.finish(heatId, actorId)

        val result = heatService.rejectResult(heatId, actorId)
        assertIs<RejectResult.Success>(result)
    }
}
