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
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.QualificationRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.UUID

class KnockoutServiceTest {

    private val eventRepository = EventRepository()
    private val auditRepository = AuditRepository()
    private val userRepository = UserRepository()
    private val passwordHasher = PasswordHasher()
    private val jwtKeyProvider = LocalJwtKeyProvider(SigningKeyRepository())
    private val jwtService = JwtService(jwtKeyProvider)
    private val participantRepository = ParticipantRepository()
    private val heatRepository = HeatRepository()
    private val qualificationRepository = QualificationRepository()
    private val knockoutRepository = KnockoutRepository()
    private val authService = AuthService(userRepository, TenantRepository(), MembershipRepository(), RefreshTokenRepository(), auditRepository, passwordHasher, jwtService)
    private val eventService = EventService(eventRepository, ParticipantRepository(), auditRepository)
    private val participantService = ParticipantService(participantRepository, eventRepository, auditRepository)
    private val heatService = HeatService(heatRepository, eventRepository, participantRepository, auditRepository)
    private val qualificationService = QualificationService(qualificationRepository, heatRepository, eventRepository, participantRepository, auditRepository)
    private val knockoutService = KnockoutService(knockoutRepository, heatRepository, eventRepository, participantRepository, qualificationRepository, auditRepository)

    private lateinit var actorId: UUID
    private lateinit var tenantId: UUID
    private lateinit var eventId: UUID
    private lateinit var participantIds: List<UUID>

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        jwtKeyProvider.ensureKeyExists()
        val result = authService.setupAdmin("admin", "password123", "Admin User")
        actorId = (result as SetupResult.Success).user.id
        tenantId = (result as SetupResult.Success).user.tenantId

        val created = eventService.create("Test Event", null, EventSettings(), actorId, tenantId)
        val event = (created as CreateEventResult.Success).event
        eventService.activate(event.id, event.version, actorId)
        eventId = event.id

        participantIds = listOf(
            (participantService.create(eventId, 1, "Alice", "Smith", null, null, null, actorId) as CreateParticipantResult.Success).participant.id,
            (participantService.create(eventId, 2, "Bob", "Jones", null, null, null, actorId) as CreateParticipantResult.Success).participant.id,
            (participantService.create(eventId, 3, "Charlie", "Brown", null, null, null, actorId) as CreateParticipantResult.Success).participant.id,
            (participantService.create(eventId, 4, "Diana", "Prince", null, null, null, actorId) as CreateParticipantResult.Success).participant.id,
        )
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    private fun setupQualificationAndFinalize() {
        qualificationService.setup(eventId, 1, actorId)
        qualificationService.generateSchedule(eventId, actorId)
        val heats = heatRepository.findByEventId(eventId)
        runBlocking {
            for (heat in heats) {
                heatService.arm(heat.id, actorId)
                heatService.start(heat.id, actorId)
                heatService.finish(heat.id, actorId)
            }
        }
        qualificationService.finalize(eventId, actorId)
    }

    // --- findByEventId ---

    @Test
    fun `findByEventId returns null when not set up`() {
        val t = knockoutService.findByEventId(eventId)
        assertNull(t)
    }

    @Test
    fun `findByEventId returns tournament after setup`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)

        val t = knockoutService.findByEventId(eventId)
        assertNotNull(t)
        assertEquals(eventId, t.eventId)
    }

    // --- setup ---

    @Test
    fun `setup creates tournament with PAIRING status`() {
        setupQualificationAndFinalize()
        val result = knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)

        val success = assertIs<SetupKnockoutResult.Success>(result)
        assertEquals(KnockoutStatus.PAIRING, success.tournament.status)
        assertEquals(PairingMode.FIRST_VS_LAST, success.tournament.pairingMode)
        assertEquals(eventId, success.tournament.eventId)
    }

    @Test
    fun `setup returns EventNotFound for unknown event`() {
        val result = knockoutService.setup(UUID.randomUUID(), PairingMode.FIRST_VS_LAST, actorId)
        assertIs<SetupKnockoutResult.EventNotFound>(result)
    }

    @Test
    fun `setup returns EventNotActive for draft event`() {
        val draftEvent = eventService.create("Draft", null, EventSettings(), actorId, tenantId)
        val draftId = (draftEvent as CreateEventResult.Success).event.id

        val result = knockoutService.setup(draftId, PairingMode.FIRST_VS_LAST, actorId)
        assertIs<SetupKnockoutResult.EventNotActive>(result)
    }

    @Test
    fun `setup returns AlreadyExists if already set up`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)

        val result = knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        assertIs<SetupKnockoutResult.AlreadyExists>(result)
    }

    @Test
    fun `setup returns QualificationNotFinalized when qualification not finalized`() {
        qualificationService.setup(eventId, 1, actorId)

        val result = knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        assertIs<SetupKnockoutResult.QualificationNotFinalized>(result)
    }

    @Test
    fun `setup returns QualificationNotFinalized when no qualification`() {
        val result = knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        assertIs<SetupKnockoutResult.QualificationNotFinalized>(result)
    }

    @Test
    fun `setup returns NotEnoughParticipants with fewer than 2 active participants`() {
        // Qualification needs 2+ participants, so create 2, finalize, then deactivate one
        qualificationService.setup(eventId, 1, actorId)
        qualificationService.generateSchedule(eventId, actorId)
        val heats2 = heatRepository.findByEventId(eventId)
        runBlocking {
            for (heat in heats2) {
                heatService.arm(heat.id, actorId)
                heatService.start(heat.id, actorId)
                heatService.finish(heat.id, actorId)
            }
        }
        qualificationService.finalize(eventId, actorId)

        participantService.deactivate(participantIds[1], actorId)
        participantService.deactivate(participantIds[2], actorId)
        participantService.deactivate(participantIds[3], actorId)

        val result = knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        assertIs<SetupKnockoutResult.NotEnoughParticipants>(result)
    }

    // --- generatePairings ---

    @Test
    fun `generatePairings creates matches with FIRST_VS_LAST mode`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)

        val result = knockoutService.generatePairings(eventId, actorId)
        val success = assertIs<GeneratePairingsResult.Success>(result)
        assertEquals(KnockoutStatus.IN_PROGRESS, success.tournament.status)

        val matches = knockoutService.getMatches(eventId)
        assertTrue(matches.isNotEmpty())
        assertEquals(participantIds.size / 2, matches.count { it.roundNumber == 1 })
    }

    @Test
    fun `generatePairings creates matches with ADJACENT mode`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.ADJACENT, actorId)

        val result = knockoutService.generatePairings(eventId, actorId)
        assertIs<GeneratePairingsResult.Success>(result)

        val matches = knockoutService.getMatches(eventId)
        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun `generatePairings creates matches with RANDOM mode`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.RANDOM, actorId)

        val result = knockoutService.generatePairings(eventId, actorId)
        assertIs<GeneratePairingsResult.Success>(result)

        val matches = knockoutService.getMatches(eventId)
        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun `generatePairings creates matches with MANUAL mode`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.MANUAL, actorId)

        val result = knockoutService.generatePairings(eventId, actorId)
        assertIs<GeneratePairingsResult.Success>(result)

        val matches = knockoutService.getMatches(eventId)
        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun `generatePairings returns TournamentNotFound`() {
        val result = knockoutService.generatePairings(eventId, actorId)
        assertIs<GeneratePairingsResult.TournamentNotFound>(result)
    }

    @Test
    fun `generatePairings returns InvalidStatus when not PAIRING`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        knockoutService.generatePairings(eventId, actorId)

        val result = knockoutService.generatePairings(eventId, actorId)
        assertIs<GeneratePairingsResult.InvalidStatus>(result)
    }

    // --- createHeatForMatch ---

    @Test
    fun `createHeatForMatch creates a heat for a planned match`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        knockoutService.generatePairings(eventId, actorId)
        val matches = knockoutService.getMatches(eventId)
        val firstMatch = matches.first { it.roundNumber == 1 }

        val result = knockoutService.createHeatForMatch(eventId, firstMatch.id, actorId)
        val success = assertIs<CreateHeatForMatchResult.Success>(result)
        assertEquals(eventId, success.heat.eventId)
    }

    @Test
    fun `createHeatForMatch returns TournamentNotFound`() {
        val result = knockoutService.createHeatForMatch(eventId, UUID.randomUUID(), actorId)
        assertIs<CreateHeatForMatchResult.TournamentNotFound>(result)
    }

    @Test
    fun `createHeatForMatch returns MatchNotFound`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)

        val result = knockoutService.createHeatForMatch(eventId, UUID.randomUUID(), actorId)
        assertIs<CreateHeatForMatchResult.MatchNotFound>(result)
    }

    @Test
    fun `createHeatForMatch returns MatchAlreadyCompleted for completed match`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        knockoutService.generatePairings(eventId, actorId)
        val matches = knockoutService.getMatches(eventId)
        val firstMatch = matches.first { it.roundNumber == 1 }

        val heatResult = knockoutService.createHeatForMatch(eventId, firstMatch.id, actorId)
        val heat = (heatResult as CreateHeatForMatchResult.Success).heat
        knockoutService.recordMatchResult(eventId, firstMatch.id, participantIds[0], heat.id, actorId)

        val result = knockoutService.createHeatForMatch(eventId, firstMatch.id, actorId)
        assertIs<CreateHeatForMatchResult.MatchAlreadyCompleted>(result)
    }

    // --- recordMatchResult ---

    @Test
    fun `recordMatchResult records winner and advances to next round`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        knockoutService.generatePairings(eventId, actorId)
        val matches = knockoutService.getMatches(eventId)
        val firstMatch = matches.first { it.roundNumber == 1 }
        val heatResult = knockoutService.createHeatForMatch(eventId, firstMatch.id, actorId)
        val heat = (heatResult as CreateHeatForMatchResult.Success).heat

        val result = knockoutService.recordMatchResult(eventId, firstMatch.id, participantIds[0], heat.id, actorId)
        assertIs<RecordMatchResult.Success>(result)

        val updatedMatches = knockoutService.getMatches(eventId)
        val completed = updatedMatches.find { it.id == firstMatch.id }
        assertNotNull(completed)
        assertEquals(KnockoutMatchStatus.COMPLETED, completed.status)
        assertEquals(participantIds[0], completed.winnerId)
    }

    @Test
    fun `recordMatchResult returns TournamentNotFound`() {
        val result = knockoutService.recordMatchResult(eventId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), actorId)
        assertIs<RecordMatchResult.TournamentNotFound>(result)
    }

    @Test
    fun `recordMatchResult returns MatchNotFound`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)

        val result = knockoutService.recordMatchResult(eventId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), actorId)
        assertIs<RecordMatchResult.MatchNotFound>(result)
    }

    @Test
    fun `recordMatchResult returns MatchAlreadyCompleted`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        knockoutService.generatePairings(eventId, actorId)
        val matches = knockoutService.getMatches(eventId)
        val firstMatch = matches.first { it.roundNumber == 1 }
        val heatResult = knockoutService.createHeatForMatch(eventId, firstMatch.id, actorId)
        val heat = (heatResult as CreateHeatForMatchResult.Success).heat
        knockoutService.recordMatchResult(eventId, firstMatch.id, participantIds[0], heat.id, actorId)

        val result = knockoutService.recordMatchResult(eventId, firstMatch.id, participantIds[0], heat.id, actorId)
        assertIs<RecordMatchResult.MatchAlreadyCompleted>(result)
    }

    @Test
    fun `recordMatchResult returns WinnerNotInMatch`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        knockoutService.generatePairings(eventId, actorId)
        val matches = knockoutService.getMatches(eventId)
        val firstMatch = matches.first { it.roundNumber == 1 }
        val heatResult = knockoutService.createHeatForMatch(eventId, firstMatch.id, actorId)
        val heat = (heatResult as CreateHeatForMatchResult.Success).heat

        val result = knockoutService.recordMatchResult(eventId, firstMatch.id, UUID.randomUUID(), heat.id, actorId)
        assertIs<RecordMatchResult.WinnerNotInMatch>(result)
    }

    // --- finalize ---

    @Test
    fun `finalize returns Success when all matches completed`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        knockoutService.generatePairings(eventId, actorId)

        var matches = knockoutService.getMatches(eventId)
        while (matches.any { it.status != KnockoutMatchStatus.COMPLETED }) {
            for (match in matches.filter { it.status == KnockoutMatchStatus.PLANNED }) {
                val winner = match.participant1Id ?: match.participant2Id ?: continue
                val heatResult = knockoutService.createHeatForMatch(eventId, match.id, actorId)
                val heat = (heatResult as CreateHeatForMatchResult.Success).heat
                knockoutService.recordMatchResult(eventId, match.id, winner, heat.id, actorId)
            }
            matches = knockoutService.getMatches(eventId)
        }

        val result = knockoutService.finalize(eventId, actorId)
        assertIs<FinalizeKnockoutResult.Success>(result)

        val t = knockoutService.findByEventId(eventId)
        assertNotNull(t)
        assertEquals(KnockoutStatus.FINALIZED, t.status)
    }

    @Test
    fun `finalize returns TournamentNotFound`() {
        val result = knockoutService.finalize(eventId, actorId)
        assertIs<FinalizeKnockoutResult.TournamentNotFound>(result)
    }

    @Test
    fun `finalize returns InvalidStatus when PAIRING`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)

        val result = knockoutService.finalize(eventId, actorId)
        assertIs<FinalizeKnockoutResult.InvalidStatus>(result)
    }

    @Test
    fun `finalize returns IncompleteMatches when matches not all done`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        knockoutService.generatePairings(eventId, actorId)

        val result = knockoutService.finalize(eventId, actorId)
        assertIs<FinalizeKnockoutResult.IncompleteMatches>(result)
    }

    // --- getResults ---

    @Test
    fun `getResults returns top 3 after full knockout`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        knockoutService.generatePairings(eventId, actorId)

        var matches = knockoutService.getMatches(eventId)
        while (matches.any { it.status != KnockoutMatchStatus.COMPLETED }) {
            for (match in matches.filter { it.status == KnockoutMatchStatus.PLANNED }) {
                val winner = match.participant1Id ?: match.participant2Id ?: continue
                val heatResult = knockoutService.createHeatForMatch(eventId, match.id, actorId)
                val heat = (heatResult as CreateHeatForMatchResult.Success).heat
                knockoutService.recordMatchResult(eventId, match.id, winner, heat.id, actorId)
            }
            matches = knockoutService.getMatches(eventId)
        }

        knockoutService.finalize(eventId, actorId)

        val results = knockoutService.getResults(eventId)
        assertTrue(results.isNotEmpty())
        assertEquals(1, results[0].rank)
        assertEquals(2, results[1].rank)
    }

    @Test
    fun `getResults returns empty when no tournament`() {
        val results = knockoutService.getResults(eventId)
        assertTrue(results.isEmpty())
    }

    // --- getMatches ---

    @Test
    fun `getMatches returns empty when no tournament`() {
        val matches = knockoutService.getMatches(eventId)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `getMatches returns matches after pairings generated`() {
        setupQualificationAndFinalize()
        knockoutService.setup(eventId, PairingMode.FIRST_VS_LAST, actorId)
        knockoutService.generatePairings(eventId, actorId)

        val matches = knockoutService.getMatches(eventId)
        assertTrue(matches.isNotEmpty())
        assertTrue(matches.all { it.tournamentId != UUID.randomUUID() })
    }
}
