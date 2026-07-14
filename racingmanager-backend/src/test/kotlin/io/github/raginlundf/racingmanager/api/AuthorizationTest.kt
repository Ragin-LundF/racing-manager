package io.github.raginlundf.racingmanager.api

import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.LoginResult
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.bootstrap.LocalPackageService
import io.github.raginlundf.racingmanager.application.diagnostics.DiagnosticsService
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.knockout.KnockoutService
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.application.results.ResultsService
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
import io.github.raginlundf.racingmanager.application.sync.SyncService
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.tenant.MembershipEntity
import io.github.raginlundf.racingmanager.domain.tenant.TenantEntity
import io.github.raginlundf.racingmanager.domain.user.UserEntity
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.configureWebSockets
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ImportedPackageRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.KnockoutRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.LocalInstanceRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.MembershipRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.PairedInstanceRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.PairingCodeRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.QualificationRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.RefreshTokenRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SpectatorExchangeCodeRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SyncedResultRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.TenantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.security.LocalJwtKeyProvider
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import io.github.raginlundf.racingmanager.infrastructure.spectator.SpectatorWebSocketService
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import java.sql.SQLException
import java.util.UUID
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Authorization boundary tests for Slice C (design §7): a token from one
tenant must never read or modify another tenant's data by ID substitution,
an insufficient scope must be rejected, and an unauthenticated request
must be rejected — independent of which route family is exercised. */
class AuthorizationTest {

    private val userRepository = UserRepository()
    private val tenantRepository = TenantRepository()
    private val membershipRepository = MembershipRepository()
    private val jwtKeyProvider = LocalJwtKeyProvider(repository = SigningKeyRepository())
    private val jwtService = JwtService(keyProvider = jwtKeyProvider)
    private val auditRepository = AuditRepository()
    private val eventRepository = EventRepository()
    private val passwordHasher = PasswordHasher()
    private val authService = AuthService(
        userRepository = userRepository,
        tenantRepository = tenantRepository,
        membershipRepository = membershipRepository,
        refreshTokenRepository = RefreshTokenRepository(),
        auditRepository = auditRepository,
        passwordHasher = passwordHasher,
        jwtService = jwtService
    )
    private val eventService = EventService(
        eventRepository = eventRepository,
        participantRepository = ParticipantRepository(),
        auditRepository = auditRepository
    )
    private val participantRepository = ParticipantRepository()
    private val participantService = ParticipantService(
        participantRepository = participantRepository,
        eventRepository = eventRepository,
        auditRepository = auditRepository
    )
    private val heatRepository = HeatRepository()
    private val heatService = HeatService(
        heatRepository = heatRepository,
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        auditRepository = auditRepository
    )
    private val qualificationRepository = QualificationRepository()
    private val qualificationService = QualificationService(
        qualificationRepository = qualificationRepository,
        heatRepository = heatRepository,
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        auditRepository = auditRepository
    )
    private val knockoutRepository = KnockoutRepository()
    private val knockoutService = KnockoutService(
        knockoutRepository = knockoutRepository,
        heatRepository = heatRepository,
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        qualificationRepository = qualificationRepository,
        auditRepository = auditRepository
    )
    private val spectatorService = SpectatorService(
        eventRepository = eventRepository,
        heatRepository = heatRepository,
        participantRepository = participantRepository,
        qualificationRepository = qualificationRepository,
        knockoutRepository = knockoutRepository
    )
    private val spectatorWebSocketService = SpectatorWebSocketService(
        spectatorService = spectatorService,
        heatRepository = heatRepository,
        heatServiceEvents = heatService.events
    )
    private val spectatorExchangeCodeRepository = SpectatorExchangeCodeRepository()
    private val importedPackageRepository = ImportedPackageRepository()
    private val localInstanceRepository = LocalInstanceRepository()
    private val localPackageService = LocalPackageService(
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        tenantRepository = tenantRepository,
        importedPackageRepository = importedPackageRepository,
        localInstanceRepository = localInstanceRepository,
        jwtKeyProvider = jwtKeyProvider
    )
    private val pairingCodeRepository = PairingCodeRepository()
    private val pairedInstanceRepository = PairedInstanceRepository()
    private val syncedResultRepository = SyncedResultRepository()
    private val syncService = SyncService(
        pairingCodeRepository = pairingCodeRepository,
        pairedInstanceRepository = pairedInstanceRepository,
        syncedResultRepository = syncedResultRepository,
        eventRepository = eventRepository,
        auditRepository = auditRepository
    )
    private val resultsService = ResultsService(
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        heatRepository = heatRepository,
        qualificationRepository = qualificationRepository,
        knockoutRepository = knockoutRepository,
        auditRepository = auditRepository
    )
    private val auditService = AuditService(auditRepository = auditRepository)
    private val diagnosticsService = DiagnosticsService(
        dataSource = object : DataSource {
            override fun getConnection() = throw SQLException("not used in authorization test")
            override fun getConnection(username: String?, password: String?) = throw SQLException(
                "not used in authorization test"
            )

            override fun getLogWriter() = null

            @Suppress("EmptyFunctionBlock")
            override fun setLogWriter(out: java.io.PrintWriter?) {
            }

            @Suppress("EmptyFunctionBlock")
            override fun setLoginTimeout(seconds: Int) {
            }

            override fun getLoginTimeout() = 0
            override fun <T> unwrap(iface: Class<T>?) = throw SQLException("not a wrapper")
            override fun isWrapperFor(iface: Class<*>?) = false
            override fun getParentLogger() = Logger.getLogger("")
        },
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        heatRepository = heatRepository,
    )

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        jwtKeyProvider.ensureKeyExists()
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    private fun Application.configureTestApp() {
        configureSerialization()
        configureStatusPages()
        configureWebSockets()
        configureRouting(
            authService = authService,
            jwtService = jwtService,
            eventService = eventService,
            participantService = participantService,
            heatService = heatService,
            qualificationService = qualificationService,
            knockoutService = knockoutService,
            resultsService = resultsService,
            spectatorService = spectatorService,
            eventRepository = eventRepository,
            webSocketService = spectatorWebSocketService,
            auditService = auditService,
            diagnosticsService = diagnosticsService,
            deploymentMode = DeploymentMode.LOCAL,
            spectatorExchangeCodeRepository = spectatorExchangeCodeRepository,
            localPackageService = localPackageService,
            syncService = syncService,
            raceDeviceGateway = testRaceDeviceGateway(),
            raceDeviceSettingsRepository = testRaceDeviceSettingsRepository()
        )
    }

    /** Creates a second tenant with its own admin — `AuthService.setupAdmin`
    always lands in the fixed local tenant (there is no multi-tenant
    registration flow yet, that's Slice D), so a genuinely separate tenant
    for isolation testing is created directly through the repositories. */
    private fun createSecondTenantAdmin(username: String): Pair<TenantEntity, UserEntity> {
        val tenant = TenantEntity(
            id = UUID.randomUUID(),
            slug = "tenant-$username",
            displayName = "Tenant $username",
            createdAt = Clock.System.now()
        )
        tenantRepository.insert(tenant)
        val user = UserEntity(
            id = UUID.randomUUID(),
            tenantId = tenant.id,
            username = username,
            passwordHash = passwordHasher.hash(password = "password123"),
            displayName = "Admin $username",
            role = UserRole.ADMIN,
            createdAt = Clock.System.now(),
        )
        userRepository.insert(user)
        membershipRepository.insert(
            MembershipEntity(
                id = UUID.randomUUID(),
                userId = user.id,
                tenantId = tenant.id,
                role = UserRole.ADMIN,
                createdAt = Clock.System.now()
            ),
        )
        return tenant to user
    }

    @Test
    fun `token from tenant A cannot read tenant B's event by id substitution`() = testApplication {
        application { configureTestApp() }

        val adminA = authService.setupAdmin(username = "admin-a", password = "password123", displayName = "Admin A")
        val tenantAId = (adminA as SetupResult.Success).user.tenantId
        val loginA = authService.login(username = "admin-a", password = "password123") as LoginResult.Success

        val createResult = eventService.create(
            name = "Tenant A Event",
            description = null,
            settings = EventSettings(),
            actorId = adminA.user.id,
            tenantId = tenantAId
        )
        val eventId = (createResult as CreateEventResult.Success).event.id

        createSecondTenantAdmin(username = "admin-b")
        val loginB = authService.login(username = "admin-b", password = "password123") as LoginResult.Success

        val response = client.get("/api/v1/events/$eventId") {
            header("Authorization", "Bearer ${loginB.accessToken}")
        }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
        // The token's own tenant sees no such leak; A can still read its own event.
        val ownResponse = client.get("/api/v1/events/$eventId") {
            header("Authorization", "Bearer ${loginA.accessToken}")
        }
        assertEquals(expected = HttpStatusCode.OK, actual = ownResponse.status)
    }

    @Test
    fun `listing events only returns the caller's tenant`() = testApplication {
        application { configureTestApp() }

        val adminA = authService.setupAdmin(
            username = "admin-a",
            password = "password123",
            displayName = "Admin A"
        ) as SetupResult.Success
        eventService.create(
            name = "Tenant A Event",
            description = null,
            settings = EventSettings(),
            actorId = adminA.user.id,
            tenantId = adminA.user.tenantId
        )
        createSecondTenantAdmin(username = "admin-b")
        val loginB = authService.login(username = "admin-b", password = "password123") as LoginResult.Success

        val response = client.get("/api/v1/events") {
            header("Authorization", "Bearer ${loginB.accessToken}")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertEquals(expected = "[]", actual = response.bodyAsText())
    }

    @Test
    fun `diagnostics bundle is scoped to the caller's own tenant`() = testApplication {
        application { configureTestApp() }

        val adminA = authService.setupAdmin(
            username = "admin-a",
            password = "password123",
            displayName = "Admin A"
        ) as SetupResult.Success
        eventService.create(
            name = "Tenant A Event",
            description = null,
            settings = EventSettings(),
            actorId = adminA.user.id,
            tenantId = adminA.user.tenantId
        )
        createSecondTenantAdmin(username = "admin-b")
        val loginB = authService.login(username = "admin-b", password = "password123") as LoginResult.Success

        val response = client.get("/api/v1/diagnostics") {
            header("Authorization", "Bearer ${loginB.accessToken}")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        val body = response.bodyAsText()
        assertTrue(actual = body.contains("\"total\":0"))
        assertTrue(actual = !body.contains("Tenant A Event"))
    }

    @Test
    fun `diagnostics recovery cannot target a heat belonging to another tenant`() = testApplication {
        application { configureTestApp() }

        val adminA = authService.setupAdmin(
            username = "admin-a",
            password = "password123",
            displayName = "Admin A"
        ) as SetupResult.Success
        val eventA = (eventService.create(
            name = "Tenant A Event",
            description = null,
            settings = EventSettings(),
            actorId = adminA.user.id,
            tenantId = adminA.user.tenantId
        ) as CreateEventResult.Success).event
        eventService.activate(id = eventA.id, expectedVersion = eventA.version, actorId = adminA.user.id)
        heatService.create(eventId = eventA.id, participantIds = emptyList(), actorId = adminA.user.id)
        val heatA = heatService.findByEventId(eventId = eventA.id).first()

        createSecondTenantAdmin(username = "admin-b")
        val loginB = authService.login(username = "admin-b", password = "password123") as LoginResult.Success

        val response = client.submitForm(
            url = "/api/v1/diagnostics/recover",
            formParameters = Parameters.build {
                append(name = "heatId", value = heatA.id.toString())
                append(name = "action", value = "cancel")
            },
        ) {
            header("Authorization", "Bearer ${loginB.accessToken}")
        }

        assertEquals(expected = HttpStatusCode.NotFound, actual = response.status)
    }

    @Test
    fun `token from tenant A cannot arm a heat belonging to tenant B's event`() = testApplication {
        application { configureTestApp() }

        // setupAdmin's first-run check is global (any existing user disables it), so the
        // local-tenant admin must be created before any other tenant's users are inserted.
        authService.setupAdmin(username = "admin-a", password = "password123", displayName = "Admin A")
        val loginA = authService.login(username = "admin-a", password = "password123") as LoginResult.Success

        val (tenantB, adminB) = createSecondTenantAdmin(username = "admin-b")
        val eventResult = eventService.create(
            name = "Tenant B Event",
            description = null,
            settings = EventSettings(),
            actorId = adminB.id,
            tenantId = tenantB.id
        )
        val eventId = (eventResult as CreateEventResult.Success).event.id

        val response = client.get("/api/v1/events/$eventId/heats") {
            header("Authorization", "Bearer ${loginA.accessToken}")
        }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
    }

    @Test
    fun `insufficient scope returns 403 on an operational route`() = testApplication {
        application { configureTestApp() }

        val spectatorOnlyToken = jwtService.issueAccessToken(
            userId = UUID.randomUUID(),
            tenantId = UUID.randomUUID(),
            scopes = setOf(Scopes.SPECTATOR),
            ttl = 15.minutes,
        )

        val response = client.post("/api/v1/events") {
            header("Authorization", "Bearer $spectatorOnlyToken")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Should not be created"}""")
        }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
    }

    @Test
    fun `unauthenticated request returns 401`() = testApplication {
        application { configureTestApp() }

        val response = client.get("/api/v1/events")

        assertEquals(expected = HttpStatusCode.Unauthorized, actual = response.status)
    }

    @Test
    fun `event-scoped audit requires rm-admin, rm-user is forbidden`() = testApplication {
        application { configureTestApp() }

        val tenant = TenantEntity(
            id = UUID.randomUUID(),
            slug = "audit-tenant",
            displayName = "Audit Tenant",
            createdAt = Clock.System.now()
        )
        tenantRepository.insert(tenant = tenant)

        val director = UserEntity(
            id = UUID.randomUUID(),
            tenantId = tenant.id,
            username = "director",
            passwordHash = passwordHasher.hash(password = "password123"),
            displayName = "Director",
            role = UserRole.DIRECTOR,
            createdAt = Clock.System.now(),
        )
        userRepository.insert(user = director)

        membershipRepository.insert(
            membership = MembershipEntity(
                id = UUID.randomUUID(),
                userId = director.id,
                tenantId = tenant.id,
                role = UserRole.DIRECTOR,
                createdAt = Clock.System.now()
            ),
        )
        val eventResult = eventService.create(
            name = "Audited Event",
            description = null,
            settings = EventSettings(),
            actorId = director.id,
            tenantId = tenant.id
        )
        val eventId = (eventResult as CreateEventResult.Success).event.id
        val login = authService.login(username = "director", password = "password123") as LoginResult.Success

        val response = client.get("/api/v1/events/$eventId/audit") {
            header("Authorization", "Bearer ${login.accessToken}")
        }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
    }
}
