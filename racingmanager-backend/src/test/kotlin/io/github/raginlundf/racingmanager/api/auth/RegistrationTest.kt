package io.github.raginlundf.racingmanager.api.auth

import io.github.raginlundf.racingmanager.api.configureRouting
import io.github.raginlundf.racingmanager.api.configureSerialization
import io.github.raginlundf.racingmanager.api.configureStatusPages
import io.github.raginlundf.racingmanager.api.testRaceDeviceGateway
import io.github.raginlundf.racingmanager.api.testRaceDeviceSettingsRepository
import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.LoginResult
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.bootstrap.LocalPackageService
import io.github.raginlundf.racingmanager.application.diagnostics.DiagnosticsService
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.knockout.KnockoutService
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.application.results.ResultsService
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
import io.github.raginlundf.racingmanager.application.sync.SyncService
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
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import java.io.PrintWriter
import java.sql.SQLException
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Slice D: public hosted-mode tenant registration and tenant-admin-managed
    user creation. */
class RegistrationTest {

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
    private val participantRepository = ParticipantRepository()
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
            override fun getConnection() = throw SQLException("not used in registration test")
            override fun getConnection(username: String?, password: String?) = throw SQLException(
                "not used in registration test"
            )
            override fun getLogWriter() = null
            @Suppress("EmptyFunctionBlock")
            override fun setLogWriter(out: PrintWriter?) {}
            @Suppress("EmptyFunctionBlock")
            override fun setLoginTimeout(seconds: Int) {}
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

    private fun Application.configureTestApp(mode: DeploymentMode) {
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
            deploymentMode = mode,
            spectatorExchangeCodeRepository = spectatorExchangeCodeRepository,
            localPackageService = localPackageService,
            syncService = syncService,
            raceDeviceGateway = testRaceDeviceGateway(),
            raceDeviceSettingsRepository = testRaceDeviceSettingsRepository()
        )
    }

    @Test
    fun `register bootstraps a fresh local install`() = testApplication {
        application { configureTestApp(DeploymentMode.LOCAL) }

        val response = client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{
                |"tenantName":"Acme Racing",
                |"tenantSlug":"acme",
                |"username":"admin",
                |"password":"password123",
                |"displayName":"Admin"
                |}""".trimMargin())
        }

        assertEquals(expected = HttpStatusCode.Created, actual = response.status)
        val body = response.bodyAsText()
        assertTrue(actual = body.contains("\"tenantSlug\":\"acme\""))
        assertTrue(actual = body.contains("\"role\":\"ADMIN\""))
        assertTrue(actual = body.contains("\"accessToken\""))
    }

    @Test
    fun `register closes in local mode once a user exists`() = testApplication {
        application { configureTestApp(DeploymentMode.LOCAL) }
        authService.setupAdmin(username = "admin", password = "password123", displayName = "Admin")

        val response = client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{
                |"tenantName":"Acme Racing",
                |"tenantSlug":"acme",
                |"username":"intruder",
                |"password":"password123",
                |"displayName":"Intruder"
                |}""".trimMargin())
        }

        assertEquals(expected = HttpStatusCode.Conflict, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"code\":\"ALREADY_SETUP\""))
    }

    @Test
    fun `register stays open in hosted mode after the first tenant exists`() = testApplication {
        application { configureTestApp(DeploymentMode.HOSTED) }
        authService.register(
            tenantDisplayName = "First Tenant",
            tenantSlug = "first",
            username = "admin",
            password = "password123",
            displayName = "Admin"
        )

        val response = client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{
                |"tenantName":"Second Tenant",
                |"tenantSlug":"second",
                |"username":"admin2",
                |"password":"password123",
                |"displayName":"Admin Two"
                |}""".trimMargin())
        }

        assertEquals(expected = HttpStatusCode.Created, actual = response.status)
    }

    @Test
    fun `register creates an isolated tenant and admin in hosted mode`() = testApplication {
        application { configureTestApp(mode = DeploymentMode.HOSTED) }

        val response = client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{
                |"tenantName":"Acme Racing",
                |"tenantSlug":"acme",
                |"username":"admin",
                |"password":"password123",
                |"displayName":"Admin"
                |}""".trimMargin())
        }

        assertEquals(expected = HttpStatusCode.Created, actual = response.status)
        val body = response.bodyAsText()
        assertTrue(actual = body.contains("\"tenantSlug\":\"acme\""))
        assertTrue(actual = body.contains("\"role\":\"ADMIN\""))
        assertTrue(actual = body.contains("\"accessToken\""))
    }

    @Test
    fun `registered admin can immediately access their own tenant's events`() = testApplication {
        application { configureTestApp(DeploymentMode.HOSTED) }

        val registerBody = client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{
                |"tenantName":"Acme Racing",
                |"tenantSlug":"acme",
                |"username":"admin",
                |"password":"password123",
                |"displayName":"Admin"
                |}""".trimMargin())
        }.bodyAsText()
        val accessToken = """"accessToken":"([^"]+)"""".toRegex().find(registerBody)!!.groupValues[1]

        val response = client.post("/api/v1/events") {
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Acme Derby"}""")
        }

        assertEquals(expected = HttpStatusCode.Created, actual = response.status)
    }

    @Test
    fun `register rejects a duplicate tenant slug`() = testApplication {
        application { configureTestApp(mode = DeploymentMode.HOSTED) }

        client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{
                |"tenantName":"Acme Racing",
                |"tenantSlug":"acme",
                |"username":"admin",
                |"password":"password123",
                |"displayName":"Admin"
                |}""".trimMargin())
        }
        val response = client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{
                |"tenantName":"Acme Racing 2",
                |"tenantSlug":"acme",
                |"username":"admin2",
                |"password":"password123",
                |"displayName":"Admin 2"
                |}""".trimMargin())
        }

        assertEquals(expected = HttpStatusCode.Conflict, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"code\":\"TENANT_SLUG_TAKEN\""))
    }

    @Test
    fun `admin creates a tenant user defaulting to DIRECTOR`() = testApplication {
        application { configureTestApp(mode = DeploymentMode.LOCAL) }

        val setupResult = authService.setupAdmin(username = "admin", password = "password123", displayName = "Admin")
        val adminUser = (setupResult as SetupResult.Success).user
        val login = authService.login(username = "admin", password = "password123") as LoginResult.Success

        val response = client.post("/api/v1/tenant/users") {
            header("Authorization", "Bearer ${login.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"username":"director","password":"password123","displayName":"Director"}""")
        }

        assertEquals(expected = HttpStatusCode.Created, actual = response.status)
        val body = response.bodyAsText()
        assertTrue(actual = body.contains("\"role\":\"DIRECTOR\""))

        // The new user can log in and only gets rm:user, not rm:admin.
        val newLoginBody = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"director","password":"password123"}""")
        }.bodyAsText()
        assertTrue(actual = newLoginBody.contains("\"scopes\":[\"rm:user\"]"))

        // The tenant now has two distinct users under the same admin's tenant.
        assertEquals(
            expected = adminUser.tenantId,
            actual = tenantRepository.findBySlug(slug = AuthService.LOCAL_TENANT_SLUG)!!.id
        )
    }

    @Test
    fun `a normal user cannot create tenant users - privilege escalation is rejected`() = testApplication {
        application { configureTestApp(mode = DeploymentMode.LOCAL) }

        val setupResult = authService.setupAdmin(
            username = "admin",
            password = "password123",
            displayName = "Admin"
        ) as SetupResult.Success
        authService.createTenantUser(
            tenantId = setupResult.user.tenantId,
            username = "director",
            password = "password123",
            displayName = "Director"
        )
        val directorLogin = authService.login(username = "director", password = "password123") as LoginResult.Success

        val response = client.post("/api/v1/tenant/users") {
            header("Authorization", "Bearer ${directorLogin.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"username":"another-admin","password":"password123","displayName":"Sneaky","role":"ADMIN"}""")
        }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
    }

    @Test
    fun `duplicate username within the same tenant is rejected`() = testApplication {
        application { configureTestApp(mode = DeploymentMode.LOCAL) }

        authService.setupAdmin(username = "admin", password = "password123", displayName = "Admin")
        val login = authService.login(username = "admin", password = "password123") as LoginResult.Success
        client.post("/api/v1/tenant/users") {
            header("Authorization", "Bearer ${login.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"username":"director","password":"password123","displayName":"Director"}""")
        }

        val response = client.post("/api/v1/tenant/users") {
            header("Authorization", "Bearer ${login.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"username":"director","password":"password123","displayName":"Director Two"}""")
        }

        assertEquals(expected = HttpStatusCode.Conflict, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"code\":\"USERNAME_TAKEN\""))
    }

    @Test
    fun `login without a tenant slug fails closed when the username is ambiguous across tenants`() = testApplication {
        application { configureTestApp(mode = DeploymentMode.HOSTED) }

        client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{
                |"tenantName":"Tenant One",
                |"tenantSlug":"tenant-one",
                |"username":"admin",
                |"password":"password123",
                |"displayName":"Admin One"
                |}""".trimMargin())
        }
        client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{
                |"tenantName":"Tenant Two",
                |"tenantSlug":"tenant-two",
                |"username":"admin",
                |"password":"password123",
                |"displayName":"Admin Two"
                |}""".trimMargin())
        }

        val ambiguous = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        assertEquals(expected = HttpStatusCode.Unauthorized, actual = ambiguous.status)

        val disambiguated = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","tenantSlug":"tenant-two"}""")
        }
        assertEquals(expected = HttpStatusCode.OK, actual = disambiguated.status)
        assertTrue(actual = disambiguated.bodyAsText().contains("\"displayName\":\"Admin Two\""))
    }
}
