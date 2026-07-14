package io.github.raginlundf.racingmanager.application.knockout

import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.participant.CreateParticipantResult
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutStatus
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KnockoutServiceTest {

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
    private val knockoutService = KnockoutService(
        knockoutRepository = knockoutRepository,
        heatRepository = heatRepository,
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        qualificationRepository = qualificationRepository,
        auditRepository = auditRepository
    )

    private lateinit var actorId: UUID
    private lateinit var tenantId: UUID
    private lateinit var eventId: UUID
    private lateinit var participantIds: List<UUID>

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

        participantIds = listOf(
            (participantService.create(
                eventId = eventId,
                startNumber = 1,
                firstName = "Alice",
                lastName = "Smith",
                club = null,
                vehicleName = null,
                vehicleCategory = null,
                actorId = actorId
            ) as CreateParticipantResult.Success).participant.id,
            (participantService.create(
                eventId = eventId,
                startNumber = 2,
                firstName = "Bob",
                lastName = "Jones",
                club = null,
                vehicleName = null,
                vehicleCategory = null,
                actorId = actorId
            ) as CreateParticipantResult.Success).participant.id,
            (participantService.create(
                eventId = eventId,
                startNumber = 3,
                firstName = "Charlie",
                lastName = "Brown",
                club = null,
                vehicleName = null,
                vehicleCategory = null,
                actorId = actorId
            ) as CreateParticipantResult.Success).participant.id,
            (participantService.create(
                eventId = eventId,
                startNumber = 4,
                firstName = "Diana",
                lastName = "Prince",
                club = null,
                vehicleName = null,
                vehicleCategory = null,
                actorId = actorId
            ) as CreateParticipantResult.Success).participant.id,
        )
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    private fun setupQualificationAndFinalize() {
        qualificationService.setup(eventId = eventId, numberOfRuns = 1, actorId = actorId)
        qualificationService.generateSchedule(eventId = eventId, actorId = actorId)
        val heats = heatRepository.findByEventId(eventId = eventId)
        runBlocking {
            for (heat in heats) {
                heatService.arm(id = heat.id, actorId = actorId)
                heatService.start(id = heat.id, actorId = actorId)
                heatService.finish(id = heat.id, actorId = actorId)
            }
        }
        qualificationService.finalize(eventId = eventId, actorId = actorId)
    }

    // --- findByEventId ---

    @Test
    fun `findByEventId returns null when not set up`() {
        val t = knockoutService.findByEventId(eventId = eventId)
        assertNull(actual = t)
    }

    @Test
    fun `findByEventId returns tournament after setup`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)

        val t = knockoutService.findByEventId(eventId = eventId)
        assertNotNull(actual = t)
        assertEquals(expected = eventId, actual = t.eventId)
    }

    // --- setup ---

    @Test
    fun `setup creates tournament with PAIRING status`() {
        setupQualificationAndFinalize()
        val result = knockoutService.setup(
            eventId = eventId,
            pairingMode = PairingMode.FIRST_VS_LAST,
            actorId = actorId
        )

        val success = assertIs<SetupKnockoutResult.Success>(value = result)
        assertEquals(expected = KnockoutStatus.PAIRING, actual = success.tournament.status)
        assertEquals(expected = PairingMode.FIRST_VS_LAST, actual = success.tournament.pairingMode)
        assertEquals(expected = eventId, actual = success.tournament.eventId)
    }

    @Test
    fun `setup returns EventNotFound for unknown event`() {
        val result = knockoutService.setup(
            eventId = UUID.randomUUID(),
            pairingMode = PairingMode.FIRST_VS_LAST,
            actorId = actorId
        )
        assertIs<SetupKnockoutResult.EventNotFound>(value = result)
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
        val draftId = (draftEvent as CreateEventResult.Success).event.id

        val result = knockoutService.setup(
            eventId = draftId,
            pairingMode = PairingMode.FIRST_VS_LAST,
            actorId = actorId
        )
        assertIs<SetupKnockoutResult.EventNotActive>(value = result)
    }

    @Test
    fun `setup returns AlreadyExists if already set up`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)

        val result = knockoutService.setup(
            eventId = eventId,
            pairingMode = PairingMode.FIRST_VS_LAST,
            actorId = actorId
        )
        assertIs<SetupKnockoutResult.AlreadyExists>(value = result)
    }

    @Test
    fun `setup returns QualificationNotFinalized when qualification not finalized`() {
        qualificationService.setup(eventId = eventId, numberOfRuns = 1, actorId = actorId)

        val result = knockoutService.setup(
            eventId = eventId,
            pairingMode = PairingMode.FIRST_VS_LAST,
            actorId = actorId
        )
        assertIs<SetupKnockoutResult.QualificationNotFinalized>(value = result)
    }

    @Test
    fun `setup returns QualificationNotFinalized when no qualification`() {
        val result = knockoutService.setup(
            eventId = eventId,
            pairingMode = PairingMode.FIRST_VS_LAST,
            actorId = actorId
        )
        assertIs<SetupKnockoutResult.QualificationNotFinalized>(value = result)
    }

    @Test
    fun `setup returns NotEnoughParticipants with fewer than 2 active participants`() {
        // Qualification needs 2+ participants, so create 2, finalize, then deactivate one
        qualificationService.setup(eventId = eventId, numberOfRuns = 1, actorId = actorId)
        qualificationService.generateSchedule(eventId = eventId, actorId = actorId)
        val heats2 = heatRepository.findByEventId(eventId = eventId)
        runBlocking {
            for (heat in heats2) {
                heatService.arm(id = heat.id, actorId = actorId)
                heatService.start(id = heat.id, actorId = actorId)
                heatService.finish(id = heat.id, actorId = actorId)
            }
        }
        qualificationService.finalize(eventId = eventId, actorId = actorId)

        participantService.deactivate(id = participantIds[1], actorId = actorId)
        participantService.deactivate(id = participantIds[2], actorId = actorId)
        participantService.deactivate(id = participantIds[3], actorId = actorId)

        val result = knockoutService.setup(
            eventId = eventId,
            pairingMode = PairingMode.FIRST_VS_LAST,
            actorId = actorId
        )
        assertIs<SetupKnockoutResult.NotEnoughParticipants>(value = result)
    }

    // --- generatePairings ---

    @Test
    fun `generatePairings creates matches with FIRST_VS_LAST mode`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)

        val result = knockoutService.generatePairings(eventId = eventId, actorId = actorId)
        val success = assertIs<GeneratePairingsResult.Success>(value = result)
        assertEquals(expected = KnockoutStatus.IN_PROGRESS, actual = success.tournament.status)

        val matches = knockoutService.getMatches(eventId = eventId)
        assertTrue(actual = matches.isNotEmpty())
        assertEquals(expected = participantIds.size / 2, actual = matches.count { it.roundNumber == 1 })
    }

    @Test
    fun `generatePairings creates matches with ADJACENT mode`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.ADJACENT, actorId = actorId)

        val result = knockoutService.generatePairings(eventId = eventId, actorId = actorId)
        assertIs<GeneratePairingsResult.Success>(value = result)

        val matches = knockoutService.getMatches(eventId = eventId)
        assertTrue(actual = matches.isNotEmpty())
    }

    @Test
    fun `generatePairings creates matches with RANDOM mode`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.RANDOM, actorId = actorId)

        val result = knockoutService.generatePairings(eventId = eventId, actorId = actorId)
        assertIs<GeneratePairingsResult.Success>(value = result)

        val matches = knockoutService.getMatches(eventId = eventId)
        assertTrue(actual = matches.isNotEmpty())
    }

    @Test
    fun `generatePairings creates matches with MANUAL mode`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.MANUAL, actorId = actorId)

        val result = knockoutService.generatePairings(eventId = eventId, actorId = actorId)
        assertIs<GeneratePairingsResult.Success>(value = result)

        val matches = knockoutService.getMatches(eventId = eventId)
        assertTrue(actual = matches.isNotEmpty())
    }

    @Test
    fun `generatePairings returns TournamentNotFound`() {
        val result = knockoutService.generatePairings(eventId = eventId, actorId = actorId)
        assertIs<GeneratePairingsResult.TournamentNotFound>(value = result)
    }

    @Test
    fun `generatePairings returns InvalidStatus when not PAIRING`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)
        knockoutService.generatePairings(eventId = eventId, actorId = actorId)

        val result = knockoutService.generatePairings(eventId = eventId, actorId = actorId)
        assertIs<GeneratePairingsResult.InvalidStatus>(value = result)
    }

    // --- createHeatForMatch ---

    @Test
    fun `createHeatForMatch creates a heat for a planned match`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)
        knockoutService.generatePairings(eventId = eventId, actorId = actorId)
        val matches = knockoutService.getMatches(eventId = eventId)
        val firstMatch = matches.first { it.roundNumber == 1 }

        val result = knockoutService.createHeatForMatch(eventId = eventId, matchId = firstMatch.id, actorId = actorId)
        val success = assertIs<CreateHeatForMatchResult.Success>(value = result)
        assertEquals(expected = eventId, actual = success.heat.eventId)
    }

    @Test
    fun `createHeatForMatch returns TournamentNotFound`() {
        val result = knockoutService.createHeatForMatch(
            eventId = eventId,
            matchId = UUID.randomUUID(),
            actorId = actorId
        )
        assertIs<CreateHeatForMatchResult.TournamentNotFound>(value = result)
    }

    @Test
    fun `createHeatForMatch returns MatchNotFound`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)

        val result = knockoutService.createHeatForMatch(
            eventId = eventId,
            matchId = UUID.randomUUID(),
            actorId = actorId
        )
        assertIs<CreateHeatForMatchResult.MatchNotFound>(value = result)
    }

    @Test
    fun `createHeatForMatch returns MatchAlreadyCompleted for completed match`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)
        knockoutService.generatePairings(eventId = eventId, actorId = actorId)
        val matches = knockoutService.getMatches(eventId = eventId)
        val firstMatch = matches.first { it.roundNumber == 1 }

        val heatResult = knockoutService.createHeatForMatch(
            eventId = eventId,
            matchId = firstMatch.id,
            actorId = actorId
        )
        val heat = (heatResult as CreateHeatForMatchResult.Success).heat
        knockoutService.recordMatchResult(
            eventId = eventId,
            matchId = firstMatch.id,
            winnerId = participantIds[0],
            heatId = heat.id,
            actorId = actorId
        )

        val result = knockoutService.createHeatForMatch(eventId = eventId, matchId = firstMatch.id, actorId = actorId)
        assertIs<CreateHeatForMatchResult.MatchAlreadyCompleted>(value = result)
    }

    // --- recordMatchResult ---

    @Test
    fun `recordMatchResult records winner and advances to next round`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)
        knockoutService.generatePairings(eventId = eventId, actorId = actorId)
        val matches = knockoutService.getMatches(eventId = eventId)
        val firstMatch = matches.first { it.roundNumber == 1 }
        val heatResult = knockoutService.createHeatForMatch(
            eventId = eventId,
            matchId = firstMatch.id,
            actorId = actorId
        )
        val heat = (heatResult as CreateHeatForMatchResult.Success).heat

        val result = knockoutService.recordMatchResult(
            eventId = eventId,
            matchId = firstMatch.id,
            winnerId = participantIds[0],
            heatId = heat.id,
            actorId = actorId
        )
        assertIs<RecordMatchResult.Success>(value = result)

        val updatedMatches = knockoutService.getMatches(eventId = eventId)
        val completed = updatedMatches.find { it.id == firstMatch.id }
        assertNotNull(actual = completed)
        assertEquals(expected = KnockoutMatchStatus.COMPLETED, actual = completed.status)
        assertEquals(expected = participantIds[0], actual = completed.winnerId)
    }

    @Test
    fun `recordMatchResult returns TournamentNotFound`() {
        val result = knockoutService.recordMatchResult(
            eventId = eventId,
            matchId = UUID.randomUUID(),
            winnerId = UUID.randomUUID(),
            heatId = UUID.randomUUID(),
            actorId = actorId
        )
        assertIs<RecordMatchResult.TournamentNotFound>(value = result)
    }

    @Test
    fun `recordMatchResult returns MatchNotFound`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)

        val result = knockoutService.recordMatchResult(
            eventId = eventId,
            matchId = UUID.randomUUID(),
            winnerId = UUID.randomUUID(),
            heatId = UUID.randomUUID(),
            actorId = actorId
        )
        assertIs<RecordMatchResult.MatchNotFound>(value = result)
    }

    @Test
    fun `recordMatchResult returns MatchAlreadyCompleted`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)
        knockoutService.generatePairings(eventId = eventId, actorId = actorId)
        val matches = knockoutService.getMatches(eventId = eventId)
        val firstMatch = matches.first { it.roundNumber == 1 }
        val heatResult = knockoutService.createHeatForMatch(
            eventId = eventId,
            matchId = firstMatch.id,
            actorId = actorId
        )
        val heat = (heatResult as CreateHeatForMatchResult.Success).heat
        knockoutService.recordMatchResult(
            eventId = eventId,
            matchId = firstMatch.id,
            winnerId = participantIds[0],
            heatId = heat.id,
            actorId = actorId
        )

        val result = knockoutService.recordMatchResult(
            eventId = eventId,
            matchId = firstMatch.id,
            winnerId = participantIds[0],
            heatId = heat.id,
            actorId = actorId
        )
        assertIs<RecordMatchResult.MatchAlreadyCompleted>(value = result)
    }

    @Test
    fun `recordMatchResult returns WinnerNotInMatch`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)
        knockoutService.generatePairings(eventId = eventId, actorId = actorId)
        val matches = knockoutService.getMatches(eventId = eventId)
        val firstMatch = matches.first { it.roundNumber == 1 }
        val heatResult = knockoutService.createHeatForMatch(
            eventId = eventId,
            matchId = firstMatch.id,
            actorId = actorId
        )
        val heat = (heatResult as CreateHeatForMatchResult.Success).heat

        val result = knockoutService.recordMatchResult(
            eventId = eventId,
            matchId = firstMatch.id,
            winnerId = UUID.randomUUID(),
            heatId = heat.id,
            actorId = actorId
        )
        assertIs<RecordMatchResult.WinnerNotInMatch>(value = result)
    }

    // --- finalize ---

    @Test
    fun `finalize returns Success when all matches completed`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)
        knockoutService.generatePairings(eventId = eventId, actorId = actorId)

        var matches = knockoutService.getMatches(eventId = eventId)
        while (matches.any { it.status != KnockoutMatchStatus.COMPLETED }) {
            for (match in matches.filter { it.status == KnockoutMatchStatus.PLANNED }) {
                val winner = match.participant1Id ?: match.participant2Id ?: continue
                val heatResult = knockoutService.createHeatForMatch(
                    eventId = eventId,
                    matchId = match.id,
                    actorId = actorId
                )
                val heat = (heatResult as CreateHeatForMatchResult.Success).heat
                knockoutService.recordMatchResult(
                    eventId = eventId,
                    matchId = match.id,
                    winnerId = winner,
                    heatId = heat.id,
                    actorId = actorId
                )
            }
            matches = knockoutService.getMatches(eventId = eventId)
        }

        val result = knockoutService.finalize(eventId = eventId, actorId = actorId)
        assertIs<FinalizeKnockoutResult.Success>(result)

        val t = knockoutService.findByEventId(eventId = eventId)
        assertNotNull(actual = t)
        assertEquals(expected = KnockoutStatus.FINALIZED, actual = t.status)
    }

    @Test
    fun `finalize returns TournamentNotFound`() {
        val result = knockoutService.finalize(eventId = eventId, actorId = actorId)
        assertIs<FinalizeKnockoutResult.TournamentNotFound>(value = result)
    }

    @Test
    fun `finalize returns InvalidStatus when PAIRING`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)

        val result = knockoutService.finalize(eventId = eventId, actorId = actorId)
        assertIs<FinalizeKnockoutResult.InvalidStatus>(value = result)
    }

    @Test
    fun `finalize returns IncompleteMatches when matches not all done`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)
        knockoutService.generatePairings(eventId = eventId, actorId = actorId)

        val result = knockoutService.finalize(eventId = eventId, actorId = actorId)
        assertIs<FinalizeKnockoutResult.IncompleteMatches>(value = result)
    }

    // --- getResults ---

    @Test
    fun `getResults returns top 3 after full knockout`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)
        knockoutService.generatePairings(eventId = eventId, actorId = actorId)

        var matches = knockoutService.getMatches(eventId = eventId)
        while (matches.any { it.status != KnockoutMatchStatus.COMPLETED }) {
            for (match in matches.filter { it.status == KnockoutMatchStatus.PLANNED }) {
                val winner = match.participant1Id ?: match.participant2Id ?: continue
                val heatResult = knockoutService.createHeatForMatch(
                    eventId = eventId,
                    matchId = match.id,
                    actorId = actorId
                )
                val heat = (heatResult as CreateHeatForMatchResult.Success).heat
                knockoutService.recordMatchResult(
                    eventId = eventId,
                    matchId = match.id,
                    winnerId = winner,
                    heatId = heat.id,
                    actorId = actorId
                )
            }
            matches = knockoutService.getMatches(eventId = eventId)
        }

        knockoutService.finalize(eventId = eventId, actorId = actorId)

        val results = knockoutService.getResults(eventId = eventId)
        assertTrue(actual = results.isNotEmpty())
        assertEquals(expected = 1, actual = results[0].rank)
        assertEquals(expected = 2, actual = results[1].rank)
    }

    @Test
    fun `getResults returns empty when no tournament`() {
        val results = knockoutService.getResults(eventId = eventId)
        assertTrue(actual = results.isEmpty())
    }

    // --- getMatches ---

    @Test
    fun `getMatches returns empty when no tournament`() {
        val matches = knockoutService.getMatches(eventId = eventId)
        assertTrue(actual = matches.isEmpty())
    }

    @Test
    fun `getMatches returns matches after pairings generated`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId = eventId, pairingMode = PairingMode.FIRST_VS_LAST, actorId = actorId)
        knockoutService.generatePairings(eventId = eventId, actorId = actorId)

        val matches = knockoutService.getMatches(eventId = eventId)
        assertTrue(actual = matches.isNotEmpty())
        assertTrue(actual = matches.all { it.tournamentId != UUID.randomUUID() })
    }
}
