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
import io.github.raginlundf.racingmanager.infrastructure.repositories.MembershipRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.RefreshTokenRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.TenantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.security.LocalJwtKeyProvider
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ParticipantServiceTest {

    private val participantRepository = ParticipantRepository()
    private val eventRepository = EventRepository()
    private val auditRepository = AuditRepository()
    private val userRepository = UserRepository()
    private val jwtKeyProvider = LocalJwtKeyProvider(repository = SigningKeyRepository())
    private val jwtService = JwtService(keyProvider = jwtKeyProvider)
    private val passwordHasher = PasswordHasher()
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

    private lateinit var actorId: UUID
    private lateinit var tenantId: UUID
    private lateinit var eventId: UUID

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        jwtKeyProvider.ensureKeyExists()
        val setupResult = authService.setupAdmin(username = "admin", password = "password123", displayName = "Admin")
        actorId = (setupResult as SetupResult.Success).user.id
        tenantId = setupResult.user.tenantId
        val eventResult = eventService.create(
            name = "Test Event",
            description = null,
            settings = EventSettings(),
            actorId = actorId,
            tenantId = tenantId
        )
        // A newly created event is already the active one — no activate step needed.
        eventId = (eventResult as CreateEventResult.Success).event.id
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    @Test
    fun `create participant returns Success`() {
        val result = participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "John",
            lastName = "Doe",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        val success = assertIs<CreateParticipantResult.Success>(value = result)
        assertEquals(expected = 1, actual = success.participant.startNumber)
        assertEquals(expected = "John", actual = success.participant.firstName)
        assertEquals(expected = "Doe", actual = success.participant.lastName)
        assertEquals(expected = ParticipantStatus.ACTIVE, actual = success.participant.status)
    }

    @Test
    fun `create participant without start number assigns next available`() {
        val first = participantService.create(
            eventId = eventId,
            startNumber = null,
            firstName = "John",
            lastName = "Doe",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        val firstSuccess = assertIs<CreateParticipantResult.Success>(value = first)
        assertEquals(expected = 1, actual = firstSuccess.participant.startNumber)

        val second = participantService.create(
            eventId = eventId,
            startNumber = null,
            firstName = "Jane",
            lastName = "Smith",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        val secondSuccess = assertIs<CreateParticipantResult.Success>(value = second)
        assertEquals(expected = 2, actual = secondSuccess.participant.startNumber)
    }

    @Test
    fun `create participant without start number continues after manually set numbers`() {
        participantService.create(
            eventId = eventId,
            startNumber = 5,
            firstName = "John",
            lastName = "Doe",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )

        val result = participantService.create(
            eventId = eventId,
            startNumber = null,
            firstName = "Jane",
            lastName = "Smith",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        val success = assertIs<CreateParticipantResult.Success>(value = result)
        assertEquals(expected = 6, actual = success.participant.startNumber)
    }

    @Test
    fun `create participant with duplicate start number returns error`() {
        participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "John",
            lastName = "Doe",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        val result = participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "Jane",
            lastName = "Smith",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        assertIs<CreateParticipantResult.DuplicateStartNumber>(value = result)
    }

    @Test
    fun `create participant for non-active event returns error`() {
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
        val result = participantService.create(
            eventId = draftId,
            startNumber = 1,
            firstName = "John",
            lastName = "Doe",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        assertIs<CreateParticipantResult.EventNotActive>(value = result)
    }

    @Test
    fun `findByEventId returns participants sorted by sortOrder`() {
        participantService.create(
            eventId = eventId,
            startNumber = 2,
            firstName = "B",
            lastName = "B",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "A",
            lastName = "A",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )

        val participants = participantService.findByEventId(eventId = eventId)
        assertEquals(expected = 2, actual = participants.size)
    }

    @Test
    fun `findById returns participant`() {
        val created = participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "John",
            lastName = "Doe",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        val id = (created as CreateParticipantResult.Success).participant.id

        val found = participantService.findById(id = id)
        assertNotNull(actual = found)
        assertEquals(expected = "John", actual = found.firstName)
    }

    @Test
    fun `update participant changes fields`() {
        val created = participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "John",
            lastName = "Doe",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        val p = (created as CreateParticipantResult.Success).participant

        val result = participantService.update(
            id = p.id,
            startNumber = 2,
            firstName = "Jane",
            lastName = "Smith",
            club = "Club",
            actorId = actorId
        )
        val success = assertIs<UpdateParticipantResult.Success>(value = result)
        assertEquals(expected = 2, actual = success.participant.startNumber)
        assertEquals(expected = "Jane", actual = success.participant.firstName)
        assertEquals(expected = "Smith", actual = success.participant.lastName)
        assertEquals(expected = "Club", actual = success.participant.club)
    }

    @Test
    fun `update with duplicate start number returns error`() {
        participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "John",
            lastName = "Doe",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        val created = participantService.create(
            eventId = eventId,
            startNumber = 2,
            firstName = "Jane",
            lastName = "Smith",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        val p = (created as CreateParticipantResult.Success).participant

        val result = participantService.update(
            id = p.id,
            startNumber = 1,
            firstName = "Jane",
            lastName = "Smith",
            club = null,
            actorId = actorId
        )
        assertIs<UpdateParticipantResult.DuplicateStartNumber>(value = result)
    }

    @Test
    fun `deactivate changes status to INACTIVE`() {
        val created = participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "John",
            lastName = "Doe",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        val p = (created as CreateParticipantResult.Success).participant

        val result = participantService.deactivate(id = p.id, actorId = actorId)
        val success = assertIs<ParticipantActionResult.Success>(value = result)
        assertEquals(expected = ParticipantStatus.INACTIVE, actual = success.participant.status)
    }

    @Test
    fun `reactivate changes status to ACTIVE`() {
        val created = participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "John",
            lastName = "Doe",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        val p = (created as CreateParticipantResult.Success).participant
        participantService.deactivate(id = p.id, actorId = actorId)

        val result = participantService.reactivate(id = p.id, actorId = actorId)
        val success = assertIs<ParticipantActionResult.Success>(value = result)
        assertEquals(expected = ParticipantStatus.ACTIVE, actual = success.participant.status)
    }

    @Test
    fun `randomize assigns sort orders`() {
        participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "A",
            lastName = "A",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        participantService.create(
            eventId = eventId,
            startNumber = 2,
            firstName = "B",
            lastName = "B",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        participantService.create(
            eventId = eventId,
            startNumber = 3,
            firstName = "C",
            lastName = "C",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )

        val result = participantService.randomize(eventId, actorId)
        assertIs<RandomizeResult.Success>(value = result)

        val participants = participantService.findByEventId(eventId = eventId)
        val orders = participants.mapNotNull { it.sortOrder }
        assertEquals(expected = 3, actual = orders.toSet().size)
        assertEquals(expected = listOf(0, 1, 2), actual = orders.sorted())
    }

    @Test
    fun `randomize with same seed produces same order`() {
        participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "A",
            lastName = "A",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        participantService.create(
            eventId = eventId,
            startNumber = 2,
            firstName = "B",
            lastName = "B",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        participantService.create(
            eventId = eventId,
            startNumber = 3,
            firstName = "C",
            lastName = "C",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )

        participantService.randomize(eventId = eventId, actorId = actorId)
        val firstMap = participantService.findByEventId(eventId = eventId).associateBy(
            keySelector = { it.id },
            valueTransform = { it.sortOrder })

        participantService.randomize(eventId = eventId, actorId = actorId, force = true)
        val secondMap = participantService.findByEventId(eventId = eventId).associateBy(
            keySelector = { it.id },
            valueTransform = { it.sortOrder })

        assertEquals(expected = firstMap, actual = secondMap)
    }

    @Test
    fun `randomize returns AlreadyRandomized on second call without force`() {
        participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "A",
            lastName = "A",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )
        participantService.randomize(eventId = eventId, actorId = actorId)

        val result = participantService.randomize(eventId = eventId, actorId = actorId)
        assertIs<RandomizeResult.AlreadyRandomized>(value = result)
    }

    @Test
    fun `importCsv creates participants and reports errors`() {
        val rows = listOf(
            CsvParticipantRow(
                startNumber = 1,
                firstName = "John",
                lastName = "Doe",
                club = null,
                vehicleName = null,
                vehicleCategory = null
            ),
            CsvParticipantRow(
                startNumber = 2,
                firstName = "Jane",
                lastName = "Smith",
                club = "Club",
                vehicleName = "Car",
                vehicleCategory = "Sports"
            ),
            CsvParticipantRow(
                startNumber = null,
                firstName = null,
                lastName = null,
                club = null,
                vehicleName = null,
                vehicleCategory = null
            ),
        )

        val result = participantService.importCsv(eventId = eventId, rows = rows, actorId = actorId)
        val completed = assertIs<ImportResult.Completed>(value = result)
        assertEquals(expected = 2, actual = completed.created.size)
        assertEquals(expected = 1, actual = completed.errors.size)
    }

    @Test
    fun `importCsv with duplicate start numbers reports errors`() {
        participantService.create(
            eventId = eventId,
            startNumber = 1,
            firstName = "Existing",
            lastName = "User",
            club = null,
            vehicleName = null,
            vehicleCategory = null,
            actorId = actorId
        )

        val rows = listOf(
            CsvParticipantRow(
                startNumber = 1,
                firstName = "John",
                lastName = "Doe",
                club = null,
                vehicleName = null,
                vehicleCategory = null
            ),
            CsvParticipantRow(
                startNumber = 2,
                firstName = "Jane",
                lastName = "Smith",
                club = null,
                vehicleName = null,
                vehicleCategory = null
            ),
        )

        val result = participantService.importCsv(eventId = eventId, rows = rows, actorId = actorId)
        val completed = assertIs<ImportResult.Completed>(value = result)
        assertEquals(expected = 1, actual = completed.created.size)
        assertEquals(expected = 1, actual = completed.errors.size)
    }
}
