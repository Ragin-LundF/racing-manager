package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.participant.CreateParticipantResult
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaspberryPiMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.MembershipRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.RefreshTokenRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.TenantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.security.LocalJwtKeyProvider
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

class HeatServiceTest {

    private val eventRepository = EventRepository()
    private val auditRepository = AuditRepository()
    private val userRepository = UserRepository()
    private val passwordHasher = PasswordHasher()
    private val jwtKeyProvider = LocalJwtKeyProvider(repository = SigningKeyRepository())
    private val jwtService = JwtService(keyProvider = jwtKeyProvider)
    private val participantRepository = ParticipantRepository()
    private val heatRepository = HeatRepository()
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
    private val measurementGateway = RaspberryPiMeasurementGateway.simulated(
        rampDelayMs = 2,
        raceMinMs = 2,
        raceMaxMs = 4,
        dnfTimeoutMs = 6,
    )
    private val heatService = HeatService(
        heatRepository = heatRepository,
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        auditRepository = auditRepository,
        measurementGateway = measurementGateway
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
    fun `create heat returns Success`() {
        val result = heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )

        val success = assertIs<CreateHeatResult.Success>(value = result)
        assertEquals(expected = HeatStatus.PLANNED, actual = success.heat.status)
        assertEquals(expected = 2, actual = success.heat.lanes.size)
        assertEquals(expected = 1, actual = success.heat.round)
        assertEquals(expected = 1, actual = success.heat.heatNumber)
    }

    @Test
    fun `create heat for non-active event returns EventNotActive`() {
        val draftEvent = eventService.create(
            name = "Draft",
            description = null,
            settings = EventSettings(),
            actorId = actorId,
            tenantId = tenantId
        )
        val draftId = (draftEvent as CreateEventResult.Success).event.id

        val result = heatService.create(eventId = draftId, participantIds = listOf(participantId1), actorId = actorId)

        assertIs<CreateHeatResult.EventNotActive>(value = result)
    }

    @Test
    fun `create heat with unknown participant returns ParticipantNotFound`() {
        val result = heatService.create(
            eventId = eventId,
            participantIds = listOf(UUID.randomUUID()),
            actorId = actorId
        )

        assertIs<CreateHeatResult.ParticipantNotFound>(value = result)
    }

    @Test
    fun `findById returns created heat`() {
        val created = heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )
        val heatId = (created as CreateHeatResult.Success).heat.id

        val found = heatService.findById(id = heatId)
        assertNotNull(actual = found)
        assertEquals(expected = heatId, actual = found.id)
    }

    @Test
    fun `findById returns null for unknown heat`() {
        val found = heatService.findById(id = UUID.randomUUID())
        assertNull(actual = found)
    }

    @Test
    fun `findByEventId returns all heats`() {
        heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )
        heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )

        val heats = heatService.findByEventId(eventId = eventId)
        assertEquals(expected = 2, actual = heats.size)
    }

    @Test
    fun `findLatestByEventId returns most recent heat`() {
        heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )

        val latest = heatService.findLatestByEventId(eventId = eventId)
        assertNotNull(actual = latest)
        assertEquals(expected = eventId, actual = latest.eventId)
        assertEquals(expected = 2, actual = latest.heatNumber)
    }

    @Test
    fun `arm changes status to ARMED`() = runBlocking {
        val created = heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )
        val heatId = (created as CreateHeatResult.Success).heat.id

        val result = heatService.arm(id = heatId, actorId = actorId)

        val success = assertIs<ArmHeatResult.Success>(value = result)
        assertEquals(expected = HeatStatus.ARMED, actual = success.heat.status)
    }

    @Test
    fun `arm unknown heat returns NotFound`(): Unit = runBlocking {
        val result = heatService.arm(id = UUID.randomUUID(), actorId = actorId)
        assertIs<ArmHeatResult.NotFound>(value = result)
    }

    @Test
    fun `cancel armed heat changes status to CANCELLED`() = runBlocking {
        val created = heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(id = heatId, actorId = actorId)

        val result = heatService.cancel(id = heatId, actorId = actorId)

        val success = assertIs<CancelHeatResult.Success>(value = result)
        assertEquals(expected = HeatStatus.CANCELLED, actual = success.heat.status)
    }

    @Test
    fun `repeat resets status to PLANNED`() = runBlocking {
        val created = heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(id = heatId, actorId = actorId)

        val result = heatService.repeat(id = heatId, actorId = actorId)

        val success = assertIs<RepeatHeatResult.Success>(value = result)
        assertEquals(expected = HeatStatus.PLANNED, actual = success.heat.status)
    }

    @Test
    fun `accept result returns Success`(): Unit = runBlocking {
        val created = heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(id = heatId, actorId = actorId)
        heatService.start(id = heatId, actorId = actorId)
        heatService.finish(id = heatId, actorId = actorId)

        val result = heatService.acceptResult(id = heatId, actorId = actorId)
        assertIs<AcceptResult.Success>(value = result)
    }

    @Test
    fun `reject result returns Success`(): Unit = runBlocking {
        val created = heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(id = heatId, actorId = actorId)
        heatService.start(id = heatId, actorId = actorId)
        heatService.finish(id = heatId, actorId = actorId)

        val result = heatService.rejectResult(id = heatId, actorId = actorId)
        assertIs<RejectResult.Success>(value = result)
    }

    @Test
    fun `start on a SIMULATED event auto-finishes the heat with one measurement per lane`() = runBlocking {
        val created = heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(id = heatId, actorId = actorId)
        heatService.start(id = heatId, actorId = actorId)

        val finished = withTimeout(2_000.milliseconds) {
            var heat = heatService.findById(id = heatId)!!
            while (heat.status != HeatStatus.FINISHED) {
                delay(duration = 10.milliseconds)
                heat = heatService.findById(id = heatId)!!
            }
            heat
        }

        assertEquals(expected = 2, actual = finished.measurements.size)
        assertTrue(
            actual = finished.measurements.all {
                it.outcome == LaneOutcome.FINISHED || it.outcome == LaneOutcome.DNF
            }
        )
    }
}
