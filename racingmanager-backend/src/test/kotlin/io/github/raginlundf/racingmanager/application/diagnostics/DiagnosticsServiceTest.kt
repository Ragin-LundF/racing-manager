package io.github.raginlundf.racingmanager.application.diagnostics

import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.heat.CreateHeatResult
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
import io.github.raginlundf.racingmanager.infrastructure.repositories.TenantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.MembershipRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.RefreshTokenRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.security.LocalJwtKeyProvider
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.UUID

class DiagnosticsServiceTest {

    private val eventRepository = EventRepository()
    private val auditRepository = AuditRepository()
    private val userRepository = UserRepository()
    private val passwordHasher = PasswordHasher()
    private val jwtKeyProvider = LocalJwtKeyProvider(SigningKeyRepository())
    private val jwtService = JwtService(jwtKeyProvider)
    private val participantRepository = ParticipantRepository()
    private val heatRepository = HeatRepository()
    private val authService = AuthService(userRepository, TenantRepository(), MembershipRepository(), RefreshTokenRepository(), auditRepository, passwordHasher, jwtService)
    private val eventService = EventService(eventRepository, ParticipantRepository(), auditRepository)
    private val participantService = ParticipantService(participantRepository, eventRepository, auditRepository)
    private val measurementGateway = SimulationMeasurementGateway()
    private val heatService = HeatService(heatRepository, eventRepository, participantRepository, auditRepository, measurementGateway)

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
        diagnosticsService = DiagnosticsService(ds!!, eventRepository, participantRepository, heatRepository)

        val result = authService.setupAdmin("admin", "password123", "Admin User")
        actorId = (result as SetupResult.Success).user.id
        tenantId = (result as SetupResult.Success).user.tenantId

        val created = eventService.create("Test Event", null, EventSettings(), actorId, tenantId)
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
    fun `checkDatabase returns connected`() {
        val status = diagnosticsService.checkDatabase()
        assertTrue(status.connected)
        assertTrue(status.pingMs >= 0)
    }

    @Test
    fun `findUnfinishedHeats returns empty when no unfinished heats`() {
        val unfinished = diagnosticsService.findUnfinishedHeats()
        assertEquals(0, unfinished.size)
    }

    @Test
    fun `findUnfinishedHeats detects armed heat`() = runBlocking {
        val created = heatService.create(eventId, listOf(participantId1, participantId2), actorId)
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(heatId, actorId)

        val unfinished = diagnosticsService.findUnfinishedHeats()
        assertEquals(1, unfinished.size)
        assertEquals(heatId, unfinished[0].heat.id)
        assertEquals(HeatStatus.ARMED, unfinished[0].heat.status)
    }

    @Test
    fun `findUnfinishedHeats detects started heat`() = runBlocking {
        val created = heatService.create(eventId, listOf(participantId1, participantId2), actorId)
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(heatId, actorId)
        heatService.start(heatId, actorId)

        val unfinished = diagnosticsService.findUnfinishedHeats()
        assertEquals(1, unfinished.size)
        assertEquals(heatId, unfinished[0].heat.id)
        assertEquals(HeatStatus.STARTED, unfinished[0].heat.status)
    }

    @Test
    fun `recoverHeat cancels armed heat`() = runBlocking {
        val created = heatService.create(eventId, listOf(participantId1, participantId2), actorId)
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(heatId, actorId)

        val result = diagnosticsService.recoverHeat(heatId, "cancel", tenantId)
        assertNotNull(result)
        assertEquals("cancelled", result.action)

        val heat = heatService.findById(heatId)
        assertNotNull(heat)
        assertEquals(HeatStatus.CANCELLED, heat.status)
    }

    @Test
    fun `recoverHeat resets started heat to planned`() = runBlocking {
        val created = heatService.create(eventId, listOf(participantId1, participantId2), actorId)
        val heatId = (created as CreateHeatResult.Success).heat.id
        heatService.arm(heatId, actorId)
        heatService.start(heatId, actorId)

        val result = diagnosticsService.recoverHeat(heatId, "reset", tenantId)
        assertNotNull(result)
        assertEquals("reset_to_planned", result.action)

        val heat = heatService.findById(heatId)
        assertNotNull(heat)
        assertEquals(HeatStatus.PLANNED, heat.status)
    }

    @Test
    fun `recoverHeat returns null for unknown heat`() {
        val result = diagnosticsService.recoverHeat(UUID.randomUUID(), "cancel", tenantId)
        assertEquals(null, result)
    }

    @Test
    fun `getBundle returns diagnostics bundle`() {
        val bundle = diagnosticsService.getBundle(tenantId)
        assertTrue(bundle.database.connected)
        assertEquals(1, bundle.events.total)
        assertEquals(1, bundle.events.active)
        assertEquals(0, bundle.unfinishedHeats.size)
    }
}