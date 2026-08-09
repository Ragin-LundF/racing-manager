package io.github.raginlundf.racingmanager.application.qualification

import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.participant.CreateParticipantResult
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.qualification.QualificationStatus
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.MembershipRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.QualificationRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.RefreshTokenRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.TenantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.security.LocalJwtKeyProvider
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.domain.heat.Measurement
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class QualificationServiceTest {

    private val eventRepository = EventRepository()
    private val auditRepository = AuditRepository()
    private val userRepository = UserRepository()
    private val passwordHasher = PasswordHasher()
    private val jwtKeyProvider = LocalJwtKeyProvider(repository = SigningKeyRepository())
    private val jwtService = JwtService(keyProvider = jwtKeyProvider)
    private val participantRepository = ParticipantRepository()
    private val heatRepository = HeatRepository()
    private val qualificationRepository = QualificationRepository()
    private val authService = AuthService(
        userRepository = userRepository,
        tenantRepository = TenantRepository(),
        membershipRepository = MembershipRepository(),
        refreshTokenRepository = RefreshTokenRepository(),
        auditRepository = auditRepository,
        passwordHasher = passwordHasher,
        jwtService = jwtService
    )
    private val eventService = EventService(
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        auditRepository = auditRepository
    )
    private val participantService = ParticipantService(
        participantRepository = participantRepository,
        eventRepository = eventRepository,
        auditRepository = auditRepository
    )
    private val heatService = HeatService(
        heatRepository = heatRepository,
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        auditRepository = auditRepository
    )
    private val qualificationService = QualificationService(
        qualificationRepository = qualificationRepository,
        heatRepository = heatRepository,
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        auditRepository = auditRepository
    )

    private lateinit var actorId: UUID
    private lateinit var tenantId: UUID
    private lateinit var eventId: UUID
    private lateinit var participantId1: UUID
    private lateinit var participantId2: UUID

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        jwtKeyProvider.ensureKeyExists()
        val result = authService.setupAdmin(username = "admin", password = "password123", displayName = "Admin User")
        actorId = (result as SetupResult.Success).user.id
        tenantId = result.user.tenantId

        val created = eventService.create(
            name = "Test Event",
            description = null,
            settings = EventSettings(),
            actorId = actorId,
            tenantId = tenantId
        )
        val event = (created as CreateEventResult.Success).event
        eventService.activate(id = event.id, expectedVersion = event.version, actorId = actorId)
        eventId = event.id

        val p1 = participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "Alice",
            lastName = "Smith",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        participantId1 = (p1 as CreateParticipantResult.Success).participant.id
        val p2 = participantService.create(
            eventId = eventId,
            startNumber = 2,
            firstName = "Bob",
            lastName = "Jones",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        participantId2 = (p2 as CreateParticipantResult.Success).participant.id
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    @Test
    fun `setup creates qualification with PENDING status`() {
        val result = qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)

        val success = assertIs<SetupQualificationResult.Success>(value = result)
        assertEquals(expected = QualificationStatus.PENDING, actual = success.qualification.status)
        assertEquals(expected = 2, actual = success.qualification.numberOfRuns)
        assertEquals(expected = eventId, actual = success.qualification.eventId)
    }

    @Test
    fun `setup returns EventNotFound for unknown event`() {
        val result = qualificationService.setup(eventId = UUID.randomUUID(), numberOfRuns = 2, actorId = actorId)

        assertIs<SetupQualificationResult.EventNotFound>(value = result)
    }

    @Test
    fun `setup returns EventNotActive for draft event`() {
        val draftEvent = eventService.create(
            name = "Draft",
            description = null,
            settings = EventSettings(),
            actorId = actorId,
            tenantId = tenantId
        )
        // A new event starts ACTIVE; creating another one returns this to DRAFT.
        eventService.create(
            name = "Takes Over",
            description = null,
            settings = EventSettings(),
            actorId = actorId,
            tenantId = tenantId
        )
        val draftId = (draftEvent as CreateEventResult.Success).event.id

        val result = qualificationService.setup(eventId = draftId, numberOfRuns = 2, actorId = actorId)

        assertIs<SetupQualificationResult.EventNotActive>(value = result)
    }

    @Test
    fun `setup returns AlreadyExists if already set up`() {
        qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)

        val result = qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)

        assertIs<SetupQualificationResult.AlreadyExists>(value = result)
    }

    @Test
    fun `setup returns NotEnoughParticipants with fewer than 2 participants`() {
        val event2 = eventService.create(
            name = "Empty Event",
            description = null,
            settings = EventSettings(),
            actorId = actorId,
            tenantId = tenantId
        )
        val e2 = (event2 as CreateEventResult.Success).event
        eventService.activate(id = e2.id, expectedVersion = e2.version, actorId = actorId)

        val result = qualificationService.setup(eventId = e2.id, numberOfRuns = 2, actorId = actorId)

        assertIs<SetupQualificationResult.NotEnoughParticipants>(value = result)
    }

    @Test
    fun `generateSchedule creates heats for all participants`() {
        qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)
        qualificationService.generateSchedule(eventId = eventId, actorId = actorId)

        val heats = heatRepository.findByEventId(eventId = eventId)
        assertTrue(actual = heats.isNotEmpty())
        assertEquals(expected = 2, actual = heats.size) // 2 participants, 2 runs = 2 heats
    }

    @Test
    fun `generateSchedule returns QualificationNotFound`() {
        val result = qualificationService.generateSchedule(eventId, actorId)

        assertIs<GenerateScheduleResult.QualificationNotFound>(value = result)
    }

    @Test
    fun `generateSchedule returns InvalidStatus if not PENDING`() {
        qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)
        qualificationService.generateSchedule(eventId = eventId, actorId = actorId)

        val result = qualificationService.generateSchedule(eventId = eventId, actorId = actorId)

        assertIs<GenerateScheduleResult.InvalidStatus>(value = result)
    }

    @Test
    fun `generateSchedule pairs the byes for odd field with even runs so there are no solo heats`() {
        addParticipant(startNumber = 3, firstName = "Carol")
        qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)
        qualificationService.generateSchedule(eventId = eventId, actorId = actorId)

        val heats = heatRepository.findByEventId(eventId = eventId)

        // 3 participants * 2 runs = 6 lane slots = 3 two-lane heats, zero solo heats.
        assertEquals(expected = 3, actual = heats.size)
        assertEquals(expected = 0, actual = heats.count { it.lanes.size == 1 })
        assertTrue(actual = heats.all { it.lanes.size == 2 })
        // Every participant races exactly numberOfRuns (2) times.
        val appearances = heats.flatMap { it.lanes }.groupingBy { it.participantId }.eachCount()
        assertEquals(expected = 3, actual = appearances.size)
        assertTrue(actual = appearances.values.all { it == 2 })
    }

    @Test
    fun `generateSchedule leaves exactly one solo heat for odd field with odd runs`() {
        addParticipant(startNumber = 3, firstName = "Carol")
        qualificationService.setup(eventId = eventId, numberOfRuns = 1, actorId = actorId)
        qualificationService.generateSchedule(eventId = eventId, actorId = actorId)

        val heats = heatRepository.findByEventId(eventId = eventId)

        // 3 participants * 1 run = 3 lane slots = one two-lane heat + one unavoidable solo.
        assertEquals(expected = 2, actual = heats.size)
        assertEquals(expected = 1, actual = heats.count { it.lanes.size == 1 })
        // Every participant races exactly once.
        val appearances = heats.flatMap { it.lanes }.groupingBy { it.participantId }.eachCount()
        assertEquals(expected = 3, actual = appearances.size)
        assertTrue(actual = appearances.values.all { it == 1 })
    }

    @Test
    fun `getRankings returns empty list when no qualification`() {
        val rankings = qualificationService.getRankings(eventId = eventId)

        assertTrue(actual = rankings.isEmpty())
    }

    @Test
    fun `getProgress returns progress data`() {
        qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)
        qualificationService.generateSchedule(eventId = eventId, actorId = actorId)

        val progress = qualificationService.getProgress(eventId = eventId)

        assertEquals(expected = 2, actual = progress.totalHeats)
        assertEquals(expected = 0, actual = progress.completedHeats)
        assertEquals(expected = 2, actual = progress.totalParticipants)
    }

    @Test
    fun `finalize returns Success when all heats completed`() = runBlocking {
        qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)
        qualificationService.generateSchedule(eventId = eventId, actorId = actorId)

        val heats = heatRepository.findByEventId(eventId = eventId)
        for (heat in heats) {
            heatService.arm(id = heat.id, actorId = actorId)
            heatService.start(id = heat.id, actorId = actorId)
            heatService.finish(id = heat.id, actorId = actorId)
        }

        val result = qualificationService.finalize(eventId = eventId, actorId = actorId)
        assertIs<FinalizeResult.Success>(value = result)

        val q = qualificationService.findByEventId(eventId = eventId)
        assertNotNull(actual = q)
        assertEquals(expected = QualificationStatus.FINALIZED, actual = q.status)
    }

    @Test
    fun `finalize returns InvalidStatus when PENDING`() {
        qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)

        val result = qualificationService.finalize(eventId = eventId, actorId = actorId)

        assertIs<FinalizeResult.InvalidStatus>(value = result)
    }

    @Test
    fun `finalize returns IncompleteHeats when heats not all done`() {
        qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)
        qualificationService.generateSchedule(eventId = eventId, actorId = actorId)

        val result = qualificationService.finalize(eventId = eventId, actorId = actorId)

        assertIs<FinalizeResult.IncompleteHeats>(value = result)
    }

    @Test
    fun `reopen returns Success`() = runBlocking {
        qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)
        qualificationService.generateSchedule(eventId = eventId, actorId = actorId)

        val heats = heatRepository.findByEventId(eventId = eventId)
        for (heat in heats) {
            heatService.arm(id = heat.id, actorId = actorId)
            heatService.start(id = heat.id, actorId = actorId)
            heatService.finish(id = heat.id, actorId = actorId)
        }
        qualificationService.finalize(eventId = eventId, actorId = actorId)

        val result = qualificationService.reopen(eventId = eventId, actorId = actorId)
        assertIs<ReopenResult.Success>(value = result)

        val q = qualificationService.findByEventId(eventId = eventId)
        assertNotNull(actual = q)
        assertEquals(expected = QualificationStatus.IN_PROGRESS, actual = q.status)
    }

    @Test
    fun `repeating a heat clears its results from live rankings`(): Unit = runBlocking {
        // Seed a finished round-1 heat with a measurement directly, so the assertion is deterministic
        // (independent of the simulated gateway's async timing).
        qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)
        qualificationService.generateSchedule(eventId = eventId, actorId = actorId)
        val heat = heatRepository.findByEventId(eventId = eventId).first { it.round == 1 && it.lanes.isNotEmpty() }
        val lane = heat.lanes.first()
        heatRepository.insertMeasurement(
            measurement = Measurement(
                id = UUID.randomUUID(),
                heatId = heat.id,
                lane = lane.lane,
                durationNanos = 1_000_000_000,
                outcome = LaneOutcome.FINISHED,
                receivedAt = heat.createdAt,
            ),
        )
        heatRepository.updateStatus(id = heat.id, status = HeatStatus.FINISHED, finishedAt = heat.createdAt)

        val before = qualificationService.getRankings(eventId = eventId).first {
            it.participantId == lane.participantId
        }
        assertEquals(expected = 1, actual = before.completedRuns)
        assertNotNull(actual = before.bestTimeNanos)

        heatService.repeat(id = heat.id, actorId = actorId)

        val after = qualificationService.getRankings(eventId = eventId).first { it.participantId == lane.participantId }
        assertEquals(expected = 0, actual = after.completedRuns)
        assertNull(actual = after.bestTimeNanos)
    }

    @Test
    fun `reopen returns InvalidStatus when not FINALIZED`() {
        qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)

        val result = qualificationService.reopen(eventId = eventId, actorId = actorId)

        assertIs<ReopenResult.InvalidStatus>(value = result)
    }

    @Test
    fun `findByEventId returns null when not set up`() {
        val q = qualificationService.findByEventId(eventId = eventId)
        assertNull(actual = q)
    }

    @Test
    fun `findByEventId returns qualification after setup`() {
        qualificationService.setup(eventId = eventId, numberOfRuns = 2, actorId = actorId)

        val q = qualificationService.findByEventId(eventId = eventId)
        assertNotNull(actual = q)
        assertEquals(expected = eventId, actual = q.eventId)
    }

    private fun addParticipant(startNumber: Int, firstName: String): UUID {
        val created = participantService.create(
            eventId = eventId,
            startNumber = startNumber,
            firstName = firstName,
            lastName = "Racer",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        return (created as CreateParticipantResult.Success).participant.id
    }
}
