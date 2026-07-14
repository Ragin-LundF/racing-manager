package io.github.raginlundf.racingmanager.application.spectator

import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.knockout.CreateHeatForMatchResult
import io.github.raginlundf.racingmanager.application.knockout.KnockoutService
import io.github.raginlundf.racingmanager.application.participant.CreateParticipantResult
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.domain.heat.Measurement
import io.github.raginlundf.racingmanager.domain.knockout.PairingMode
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.KnockoutRepository
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
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpectatorServiceTest {

    private val eventRepository = EventRepository()
    private val auditRepository = AuditRepository()
    private val userRepository = UserRepository()
    private val passwordHasher = PasswordHasher()
    private val jwtKeyProvider = LocalJwtKeyProvider(repository = SigningKeyRepository())
    private val jwtService = JwtService(keyProvider = jwtKeyProvider)
    private val participantRepository = ParticipantRepository()
    private val heatRepository = HeatRepository()
    private val qualificationRepository = QualificationRepository()
    private val knockoutRepository = KnockoutRepository()
    private val authService = AuthService(
        userRepository = userRepository,
        tenantRepository = TenantRepository(),
        membershipRepository = MembershipRepository(),
        refreshTokenRepository = RefreshTokenRepository(),
        auditRepository = auditRepository,
        passwordHasher = passwordHasher,
        jwtService = jwtService,
    )
    private val eventService = EventService(
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        auditRepository = auditRepository,
    )
    private val participantService = ParticipantService(
        participantRepository = participantRepository,
        eventRepository = eventRepository,
        auditRepository = auditRepository,
    )
    private val heatService = HeatService(
        heatRepository = heatRepository,
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        auditRepository = auditRepository,
    )
    private val qualificationService = QualificationService(
        qualificationRepository = qualificationRepository,
        heatRepository = heatRepository,
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        auditRepository = auditRepository,
    )
    private val knockoutService = KnockoutService(
        knockoutRepository = knockoutRepository,
        heatRepository = heatRepository,
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        qualificationRepository = qualificationRepository,
        auditRepository = auditRepository,
    )
    private val spectatorService = SpectatorService(
        eventRepository = eventRepository,
        heatRepository = heatRepository,
        participantRepository = participantRepository,
        qualificationRepository = qualificationRepository,
        knockoutRepository = knockoutRepository,
    )

    private lateinit var actorId: UUID
    private lateinit var eventId: UUID

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        jwtKeyProvider.ensureKeyExists()
        val admin = (authService.setupAdmin("admin", "password123", "Admin User") as SetupResult.Success).user
        actorId = admin.id
        val created = eventService.create(
            name = "Test Event",
            description = null,
            settings = EventSettings(),
            actorId = actorId,
            tenantId = admin.tenantId,
        )
        val event = (created as CreateEventResult.Success).event
        eventService.activate(id = event.id, expectedVersion = event.version, actorId = actorId)
        eventId = event.id
        listOf("Alice" to 1, "Bob" to 2, "Charlie" to 3, "Diana" to 4).forEach { (name, no) ->
            participantService.create(
                eventId = eventId, startNumber = no, firstName = name, lastName = "X",
                club = null, vehicleName = null, vehicleCategory = null, actorId = actorId,
            ) as CreateParticipantResult.Success
        }
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    @Test
    fun `snapshot has no knockout standings before the knockout phase`() {
        qualificationService.setup(eventId = eventId, numberOfRuns = 1, actorId = actorId)
        val snapshot = spectatorService.getSnapshot(eventId)
        assertNotNull(snapshot)
        assertTrue(snapshot.knockoutStandings.isEmpty())
    }

    @Test
    fun `knockout standings carry best knockout time and WON state`() = runBlocking {
        // Qualification: run all heats and finalize.
        qualificationService.setup(eventId = eventId, numberOfRuns = 1, actorId = actorId)
        qualificationService.generateSchedule(eventId = eventId, actorId = actorId)
        for (heat in heatRepository.findByEventId(eventId)) {
            heatService.arm(id = heat.id, actorId = actorId)
            heatService.start(id = heat.id, actorId = actorId)
            heatService.finish(id = heat.id, actorId = actorId)
        }
        qualificationService.finalize(eventId = eventId, actorId = actorId)

        // Knockout: pair, create a heat for the first match, seed a decisive result, record it.
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)
        knockoutService.generatePairings(eventId = eventId, actorId = actorId)
        val match1 = knockoutService.getMatches(eventId).first { it.roundNumber == 1 && it.matchNumber == 1 }
        val heat = (knockoutService.createHeatForMatch(
            eventId = eventId, matchId = match1.id, actorId = actorId
        ) as CreateHeatForMatchResult.Success).heat
        heatRepository.insertMeasurement(
            Measurement(UUID.randomUUID(), heat.id, 1, 1_000_000_000, LaneOutcome.FINISHED, heat.createdAt),
        )
        heatRepository.insertMeasurement(
            Measurement(UUID.randomUUID(), heat.id, 2, 2_000_000_000, LaneOutcome.FINISHED, heat.createdAt),
        )
        knockoutService.recordResultFromHeat(eventId = eventId, heatId = heat.id, actorId = actorId)

        val snapshot = spectatorService.getSnapshot(eventId)
        assertNotNull(snapshot)
        assertEquals(expected = 4, actual = snapshot.knockoutStandings.size)

        val winner = snapshot.knockoutStandings.first { it.participantId == match1.participant1Id }
        assertEquals(expected = "WON", actual = winner.state)
        assertEquals(expected = 1_000_000_000L, actual = winner.bestKnockoutTimeNanos)

        val loser = snapshot.knockoutStandings.first { it.participantId == match1.participant2Id }
        assertEquals(expected = "OUT", actual = loser.state)
    }
}
