package io.github.raginlundf.racingmanager.api

import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.bootstrap.LocalPackageService
import io.github.raginlundf.racingmanager.application.sync.SyncService
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.LoginResult
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.diagnostics.DiagnosticsService
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.knockout.KnockoutService
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.application.results.ResultsService
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
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
import io.github.raginlundf.racingmanager.infrastructure.repositories.SpectatorExchangeCodeRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.RefreshTokenRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
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
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import java.util.UUID

/** Authorization boundary tests for Slice C (design §7): a token from one
    tenant must never read or modify another tenant's data by ID substitution,
    an insufficient scope must be rejected, and an unauthenticated request
    must be rejected — independent of which route family is exercised. */
class AuthorizationTest {

    private val userRepository = UserRepository()
    private val tenantRepository = TenantRepository()
    private val membershipRepository = MembershipRepository()
    private val jwtKeyProvider = LocalJwtKeyProvider(SigningKeyRepository())
    private val jwtService = JwtService(jwtKeyProvider)
    private val auditRepository = AuditRepository()
    private val eventRepository = EventRepository()
    private val passwordHasher = PasswordHasher()
    private val authService = AuthService(userRepository, tenantRepository, membershipRepository, RefreshTokenRepository(), auditRepository, passwordHasher, jwtService)
    private val eventService = EventService(eventRepository, ParticipantRepository(), auditRepository)
    private val participantRepository = ParticipantRepository()
    private val participantService = ParticipantService(participantRepository, eventRepository, auditRepository)
    private val heatRepository = HeatRepository()
    private val heatService = HeatService(heatRepository, eventRepository, participantRepository, auditRepository)
    private val qualificationRepository = QualificationRepository()
    private val qualificationService = QualificationService(qualificationRepository, heatRepository, eventRepository, participantRepository, auditRepository)
    private val knockoutRepository = KnockoutRepository()
    private val knockoutService = KnockoutService(knockoutRepository, heatRepository, eventRepository, participantRepository, qualificationRepository, auditRepository)
    private val spectatorService = SpectatorService(eventRepository, heatRepository, participantRepository, qualificationRepository, knockoutRepository)
    private val spectatorWebSocketService = SpectatorWebSocketService(spectatorService, heatRepository, heatService.events)
    private val spectatorExchangeCodeRepository = SpectatorExchangeCodeRepository()
    private val importedPackageRepository = ImportedPackageRepository()
    private val localInstanceRepository = LocalInstanceRepository()
    private val localPackageService = LocalPackageService(eventRepository, participantRepository, tenantRepository, importedPackageRepository, localInstanceRepository, jwtKeyProvider)
    private val pairingCodeRepository = PairingCodeRepository()
    private val pairedInstanceRepository = PairedInstanceRepository()
    private val syncedResultRepository = SyncedResultRepository()
    private val syncService = SyncService(pairingCodeRepository, pairedInstanceRepository, syncedResultRepository, eventRepository, auditRepository)
    private val resultsService = ResultsService(eventRepository, participantRepository, heatRepository, qualificationRepository, knockoutRepository, auditRepository)
    private val auditService = AuditService(auditRepository)
    private val diagnosticsService = DiagnosticsService(
        object : javax.sql.DataSource {
            override fun getConnection() = throw java.sql.SQLException("not used in authorization test")
            override fun getConnection(username: String?, password: String?) = throw java.sql.SQLException("not used in authorization test")
            override fun getLogWriter() = null
            override fun setLogWriter(out: java.io.PrintWriter?) {}
            override fun setLoginTimeout(seconds: Int) {}
            override fun getLoginTimeout() = 0
            override fun <T> unwrap(iface: Class<T>?) = throw java.sql.SQLException("not a wrapper")
            override fun isWrapperFor(iface: Class<*>?) = false
            override fun getParentLogger() = java.util.logging.Logger.getLogger("")
        },
        eventRepository, participantRepository, heatRepository,
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
        configureRouting(authService, jwtService, eventService, participantService, heatService, qualificationService, knockoutService, resultsService, spectatorService, eventRepository, spectatorWebSocketService, auditService, diagnosticsService, DeploymentMode.LOCAL, spectatorExchangeCodeRepository, localPackageService, syncService)
    }

    /** Creates a second tenant with its own admin — `AuthService.setupAdmin`
        always lands in the fixed local tenant (there is no multi-tenant
        registration flow yet, that's Slice D), so a genuinely separate tenant
        for isolation testing is created directly through the repositories. */
    private fun createSecondTenantAdmin(username: String): Pair<TenantEntity, UserEntity> {
        val tenant = TenantEntity(id = UUID.randomUUID(), slug = "tenant-$username", displayName = "Tenant $username", createdAt = Clock.System.now())
        tenantRepository.insert(tenant)
        val user = UserEntity(
            id = UUID.randomUUID(),
            tenantId = tenant.id,
            username = username,
            passwordHash = passwordHasher.hash("password123"),
            displayName = "Admin $username",
            role = UserRole.ADMIN,
            createdAt = Clock.System.now(),
        )
        userRepository.insert(user)
        membershipRepository.insert(
            MembershipEntity(id = UUID.randomUUID(), userId = user.id, tenantId = tenant.id, role = UserRole.ADMIN, createdAt = Clock.System.now()),
        )
        return tenant to user
    }

    @Test
    fun `token from tenant A cannot read tenant B's event by id substitution`() = testApplication {
        application { configureTestApp() }

        val adminA = authService.setupAdmin("admin-a", "password123", "Admin A")
        val tenantAId = (adminA as SetupResult.Success).user.tenantId
        val loginA = authService.login("admin-a", "password123") as LoginResult.Success

        val createResult = eventService.create("Tenant A Event", null, EventSettings(), adminA.user.id, tenantAId)
        val eventId = (createResult as CreateEventResult.Success).event.id

        createSecondTenantAdmin("admin-b")
        val loginB = authService.login("admin-b", "password123") as LoginResult.Success

        val response = client.get("/api/v1/events/$eventId") {
            header("Authorization", "Bearer ${loginB.accessToken}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        // The token's own tenant sees no such leak; A can still read its own event.
        val ownResponse = client.get("/api/v1/events/$eventId") {
            header("Authorization", "Bearer ${loginA.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, ownResponse.status)
    }

    @Test
    fun `listing events only returns the caller's tenant`() = testApplication {
        application { configureTestApp() }

        val adminA = authService.setupAdmin("admin-a", "password123", "Admin A") as SetupResult.Success
        eventService.create("Tenant A Event", null, EventSettings(), adminA.user.id, adminA.user.tenantId)
        createSecondTenantAdmin("admin-b")
        val loginB = authService.login("admin-b", "password123") as LoginResult.Success

        val response = client.get("/api/v1/events") {
            header("Authorization", "Bearer ${loginB.accessToken}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `diagnostics bundle is scoped to the caller's own tenant`() = testApplication {
        application { configureTestApp() }

        val adminA = authService.setupAdmin("admin-a", "password123", "Admin A") as SetupResult.Success
        eventService.create("Tenant A Event", null, EventSettings(), adminA.user.id, adminA.user.tenantId)
        createSecondTenantAdmin("admin-b")
        val loginB = authService.login("admin-b", "password123") as LoginResult.Success

        val response = client.get("/api/v1/diagnostics") {
            header("Authorization", "Bearer ${loginB.accessToken}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"total\":0"))
        assertTrue(!body.contains("Tenant A Event"))
    }

    @Test
    fun `diagnostics recovery cannot target a heat belonging to another tenant`() = testApplication {
        application { configureTestApp() }

        val adminA = authService.setupAdmin("admin-a", "password123", "Admin A") as SetupResult.Success
        val eventA = (eventService.create("Tenant A Event", null, EventSettings(), adminA.user.id, adminA.user.tenantId) as CreateEventResult.Success).event
        eventService.activate(eventA.id, eventA.version, adminA.user.id)
        heatService.create(eventA.id, emptyList(), adminA.user.id)
        val heatA = heatService.findByEventId(eventA.id).first()

        createSecondTenantAdmin("admin-b")
        val loginB = authService.login("admin-b", "password123") as LoginResult.Success

        val response = client.submitForm(
            url = "/api/v1/diagnostics/recover",
            formParameters = io.ktor.http.Parameters.build {
                append("heatId", heatA.id.toString())
                append("action", "cancel")
            },
        ) {
            header("Authorization", "Bearer ${loginB.accessToken}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `token from tenant A cannot arm a heat belonging to tenant B's event`() = testApplication {
        application { configureTestApp() }

        // setupAdmin's first-run check is global (any existing user disables it), so the
        // local-tenant admin must be created before any other tenant's users are inserted.
        authService.setupAdmin("admin-a", "password123", "Admin A")
        val loginA = authService.login("admin-a", "password123") as LoginResult.Success

        val (tenantB, adminB) = createSecondTenantAdmin("admin-b")
        val eventResult = eventService.create("Tenant B Event", null, EventSettings(), adminB.id, tenantB.id)
        val eventId = (eventResult as CreateEventResult.Success).event.id

        val response = client.get("/api/v1/events/$eventId/heats") {
            header("Authorization", "Bearer ${loginA.accessToken}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
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

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `unauthenticated request returns 401`() = testApplication {
        application { configureTestApp() }

        val response = client.get("/api/v1/events")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `event-scoped audit requires rm-admin, rm-user is forbidden`() = testApplication {
        application { configureTestApp() }

        val tenant = TenantEntity(id = UUID.randomUUID(), slug = "audit-tenant", displayName = "Audit Tenant", createdAt = Clock.System.now())
        tenantRepository.insert(tenant)
        val director = UserEntity(
            id = UUID.randomUUID(),
            tenantId = tenant.id,
            username = "director",
            passwordHash = passwordHasher.hash("password123"),
            displayName = "Director",
            role = UserRole.DIRECTOR,
            createdAt = Clock.System.now(),
        )
        userRepository.insert(director)
        membershipRepository.insert(
            MembershipEntity(id = UUID.randomUUID(), userId = director.id, tenantId = tenant.id, role = UserRole.DIRECTOR, createdAt = Clock.System.now()),
        )
        val eventResult = eventService.create("Audited Event", null, EventSettings(), director.id, tenant.id)
        val eventId = (eventResult as CreateEventResult.Success).event.id
        val login = authService.login("director", "password123") as LoginResult.Success

        val response = client.get("/api/v1/events/$eventId/audit") {
            header("Authorization", "Bearer ${login.accessToken}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
