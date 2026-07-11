package io.github.raginlundf.racingmanager.application.qualification

import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.CreateHeatResult
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.participant.CreateParticipantResult
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.qualification.QualificationStatus
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.QualificationRepository
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
import kotlin.test.assertTrue
import java.util.UUID

class QualificationServiceTest {

    private val eventRepository = EventRepository()
    private val auditRepository = AuditRepository()
    private val userRepository = UserRepository()
    private val passwordHasher = PasswordHasher()
    private val sessionRepository = SessionRepository()
    private val participantRepository = ParticipantRepository()
    private val heatRepository = HeatRepository()
    private val qualificationRepository = QualificationRepository()
    private val authService = AuthService(userRepository, sessionRepository, auditRepository, passwordHasher)
    private val eventService = EventService(eventRepository, ParticipantRepository(), auditRepository)
    private val participantService = ParticipantService(participantRepository, eventRepository, auditRepository)
    private val heatService = HeatService(heatRepository, eventRepository, participantRepository, auditRepository)
    private val qualificationService = QualificationService(qualificationRepository, heatRepository, eventRepository, participantRepository, auditRepository)

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
    fun `setup creates qualification with PENDING status`() {
        val result = qualificationService.setup(eventId, 2, actorId)

        val success = assertIs<SetupQualificationResult.Success>(result)
        assertEquals(QualificationStatus.PENDING, success.qualification.status)
        assertEquals(2, success.qualification.numberOfRuns)
        assertEquals(eventId, success.qualification.eventId)
    }

    @Test
    fun `setup returns EventNotFound for unknown event`() {
        val result = qualificationService.setup(UUID.randomUUID(), 2, actorId)

        assertIs<SetupQualificationResult.EventNotFound>(result)
    }

    @Test
    fun `setup returns EventNotActive for draft event`() {
        val draftEvent = eventService.create("Draft", null, EventSettings(), actorId)
        val draftId = (draftEvent as CreateEventResult.Success).event.id

        val result = qualificationService.setup(draftId, 2, actorId)

        assertIs<SetupQualificationResult.EventNotActive>(result)
    }

    @Test
    fun `setup returns AlreadyExists if already set up`() {
        qualificationService.setup(eventId, 2, actorId)

        val result = qualificationService.setup(eventId, 2, actorId)

        assertIs<SetupQualificationResult.AlreadyExists>(result)
    }

    @Test
    fun `setup returns NotEnoughParticipants with fewer than 2 participants`() {
        val event2 = eventService.create("Empty Event", null, EventSettings(), actorId)
        val e2 = (event2 as CreateEventResult.Success).event
        eventService.activate(e2.id, e2.version, actorId)

        val result = qualificationService.setup(e2.id, 2, actorId)

        assertIs<SetupQualificationResult.NotEnoughParticipants>(result)
    }

    @Test
    fun `generateSchedule creates heats for all participants`() {
        qualificationService.setup(eventId, 2, actorId)
        qualificationService.generateSchedule(eventId, actorId)

        val heats = heatRepository.findByEventId(eventId)
        assertTrue(heats.isNotEmpty())
        assertEquals(2, heats.size) // 2 participants, 2 runs = 2 heats
    }

    @Test
    fun `generateSchedule returns QualificationNotFound`() {
        val result = qualificationService.generateSchedule(eventId, actorId)

        assertIs<GenerateScheduleResult.QualificationNotFound>(result)
    }

    @Test
    fun `generateSchedule returns InvalidStatus if not PENDING`() {
        qualificationService.setup(eventId, 2, actorId)
        qualificationService.generateSchedule(eventId, actorId)

        val result = qualificationService.generateSchedule(eventId, actorId)

        assertIs<GenerateScheduleResult.InvalidStatus>(result)
    }

    @Test
    fun `getRankings returns empty list when no qualification`() {
        val rankings = qualificationService.getRankings(eventId)

        assertTrue(rankings.isEmpty())
    }

    @Test
    fun `getProgress returns progress data`() {
        qualificationService.setup(eventId, 2, actorId)
        qualificationService.generateSchedule(eventId, actorId)

        val progress = qualificationService.getProgress(eventId)

        assertEquals(2, progress.totalHeats)
        assertEquals(0, progress.completedHeats)
        assertEquals(2, progress.totalParticipants)
    }

    @Test
    fun `finalize returns Success when all heats completed`() = runBlocking {
        qualificationService.setup(eventId, 2, actorId)
        qualificationService.generateSchedule(eventId, actorId)

        val heats = heatRepository.findByEventId(eventId)
        for (heat in heats) {
            heatService.arm(heat.id, actorId)
            heatService.start(heat.id, actorId)
            heatService.finish(heat.id, actorId)
        }

        val result = qualificationService.finalize(eventId, actorId)
        assertIs<FinalizeResult.Success>(result)

        val q = qualificationService.findByEventId(eventId)
        assertNotNull(q)
        assertEquals(QualificationStatus.FINALIZED, q.status)
    }

    @Test
    fun `finalize returns InvalidStatus when PENDING`() {
        qualificationService.setup(eventId, 2, actorId)

        val result = qualificationService.finalize(eventId, actorId)

        assertIs<FinalizeResult.InvalidStatus>(result)
    }

    @Test
    fun `finalize returns IncompleteHeats when heats not all done`() {
        qualificationService.setup(eventId, 2, actorId)
        qualificationService.generateSchedule(eventId, actorId)

        val result = qualificationService.finalize(eventId, actorId)

        assertIs<FinalizeResult.IncompleteHeats>(result)
    }

    @Test
    fun `reopen returns Success`() = runBlocking {
        qualificationService.setup(eventId, 2, actorId)
        qualificationService.generateSchedule(eventId, actorId)

        val heats = heatRepository.findByEventId(eventId)
        for (heat in heats) {
            heatService.arm(heat.id, actorId)
            heatService.start(heat.id, actorId)
            heatService.finish(heat.id, actorId)
        }
        qualificationService.finalize(eventId, actorId)

        val result = qualificationService.reopen(eventId, actorId)
        assertIs<ReopenResult.Success>(result)

        val q = qualificationService.findByEventId(eventId)
        assertNotNull(q)
        assertEquals(QualificationStatus.IN_PROGRESS, q.status)
    }

    @Test
    fun `reopen returns InvalidStatus when not FINALIZED`() {
        qualificationService.setup(eventId, 2, actorId)

        val result = qualificationService.reopen(eventId, actorId)

        assertIs<ReopenResult.InvalidStatus>(result)
    }

    @Test
    fun `findByEventId returns null when not set up`() {
        val q = qualificationService.findByEventId(eventId)
        assertNull(q)
    }

    @Test
    fun `findByEventId returns qualification after setup`() {
        qualificationService.setup(eventId, 2, actorId)

        val q = qualificationService.findByEventId(eventId)
        assertNotNull(q)
        assertEquals(eventId, q.eventId)
    }
}
