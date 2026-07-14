package io.github.raginlundf.racingmanager.application.diagnostics

import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.CreateHeatResult
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.participant.CreateParticipantResult
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
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
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiagnosticsServiceTest {

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
    private val measurementGateway = RaspberryPiMeasurementGateway.simulated()
    private val heatService = HeatService(
        heatRepository = heatRepository,
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        auditRepository = auditRepository,
        measurementGateway = measurementGateway
    )

    private lateinit var diagnosticsService: DiagnosticsService
    private lateinit var actorId: UUID
    private lateinit var tenantId: UUID
    private lateinit var eventId: UUID
    private lateinit var participantId1: UUID
    private lateinit var participantId2: UUID

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        jwtKeyProvider.ensureKeyExists()
        val ds = DatabaseTestHelper.dataSource
        diagnosticsService = DiagnosticsService(
            dataSource = ds!!,
            eventRepository = eventRepository,
            participantRepository = participantRepository,
            heatRepository = heatRepository
        )

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
    fun `checkDatabase returns connected`() {
        val status = diagnosticsService.checkDatabase()
        assertTrue(actual = status.connected)
        assertTrue(actual = status.pingMs >= 0)
    }

    @Test
    fun `findUnfinishedHeats returns empty when no unfinished heats`() {
        val unfinished = diagnosticsService.findUnfinishedHeats()
        assertEquals(expected = 0, actual = unfinished.size)
    }

    @Test
    fun `findUnfinishedHeats detects armed heat`() = runBlocking {
        val created = heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(id = heatId, actorId = actorId)

        val unfinished = diagnosticsService.findUnfinishedHeats()
        assertEquals(expected = 1, actual = unfinished.size)
        assertEquals(expected = heatId, actual = unfinished[0].heat.id)
        assertEquals(expected = HeatStatus.ARMED, actual = unfinished[0].heat.status)
    }

    @Test
    fun `findUnfinishedHeats detects started heat`() = runBlocking {
        val created = heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(id = heatId, actorId = actorId)
        heatService.start(id = heatId, actorId = actorId)

        val unfinished = diagnosticsService.findUnfinishedHeats()
        assertEquals(expected = 1, actual = unfinished.size)
        assertEquals(expected = heatId, actual = unfinished[0].heat.id)
        assertEquals(expected = HeatStatus.STARTED, actual = unfinished[0].heat.status)
    }

    @Test
    fun `recoverHeat cancels armed heat`() = runBlocking {
        val created = heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(id = heatId, actorId = actorId)

        val result = diagnosticsService.recoverHeat(heatId = heatId, action = "cancel", tenantId = tenantId)
        assertNotNull(actual = result)
        assertEquals(expected = "cancelled", actual = result.action)

        val heat = heatService.findById(id = heatId)
        assertNotNull(actual = heat)
        assertEquals(expected = HeatStatus.CANCELLED, actual = heat.status)
    }

    @Test
    fun `recoverHeat resets started heat to planned`() = runBlocking {
        val created = heatService.create(
            eventId = eventId,
            participantIds = listOf(participantId1, participantId2),
            actorId = actorId
        )
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(id = heatId, actorId = actorId)
        heatService.start(id = heatId, actorId = actorId)

        val result = diagnosticsService.recoverHeat(heatId = heatId, action = "reset", tenantId = tenantId)
        assertNotNull(actual = result)
        assertEquals(expected = "reset_to_planned", actual = result.action)

        val heat = heatService.findById(id = heatId)
        assertNotNull(actual = heat)
        assertEquals(expected = HeatStatus.PLANNED, actual = heat.status)
    }

    @Test
    fun `recoverHeat returns null for unknown heat`() {
        val result = diagnosticsService.recoverHeat(heatId = UUID.randomUUID(), action = "cancel", tenantId = tenantId)
        assertEquals(expected = null, actual = result)
    }

    @Test
    fun `getBundle returns diagnostics bundle`() {
        val bundle = diagnosticsService.getBundle(tenantId = tenantId)
        assertTrue(actual = bundle.database.connected)
        assertEquals(expected = 1, actual = bundle.events.total)
        assertEquals(expected = 1, actual = bundle.events.active)
        assertEquals(expected = 0, actual = bundle.unfinishedHeats.size)
    }
}
