package io.github.raginlundf.racingmanager.api.auth

import io.github.raginlundf.racingmanager.api.configureRouting
import io.github.raginlundf.racingmanager.api.configureSerialization
import io.github.raginlundf.racingmanager.api.configureStatusPages
import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.bootstrap.LocalPackageService
import io.github.raginlundf.racingmanager.application.sync.SyncService
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.diagnostics.DiagnosticsService
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.knockout.KnockoutService
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.application.results.ResultsService
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
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

/** Slice D: public hosted-mode tenant registration and tenant-admin-managed
    user creation. */
class RegistrationTest {

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
            override fun getConnection() = throw java.sql.SQLException("not used in registration test")
            override fun getConnection(username: String?, password: String?) = throw java.sql.SQLException("not used in registration test")
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

    private fun Application.configureTestApp(mode: DeploymentMode) {
        configureSerialization()
        configureStatusPages()
        configureWebSockets()
        configureRouting(authService, jwtService, eventService, participantService, heatService, qualificationService, knockoutService, resultsService, spectatorService, eventRepository, spectatorWebSocketService, auditService, diagnosticsService, mode, spectatorExchangeCodeRepository, localPackageService, syncService)
    }

    @Test
    fun `register is forbidden in local mode`() = testApplication {
        application { configureTestApp(DeploymentMode.LOCAL) }

        val response = client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantName":"Acme Racing","tenantSlug":"acme","username":"admin","password":"password123","displayName":"Admin"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"NOT_HOSTED\""))
    }

    @Test
    fun `register creates an isolated tenant and admin in hosted mode`() = testApplication {
        application { configureTestApp(DeploymentMode.HOSTED) }

        val response = client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantName":"Acme Racing","tenantSlug":"acme","username":"admin","password":"password123","displayName":"Admin"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"tenantSlug\":\"acme\""))
        assertTrue(body.contains("\"role\":\"ADMIN\""))
        assertTrue(body.contains("\"accessToken\""))
    }

    @Test
    fun `registered admin can immediately access their own tenant's events`() = testApplication {
        application { configureTestApp(DeploymentMode.HOSTED) }

        val registerBody = client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantName":"Acme Racing","tenantSlug":"acme","username":"admin","password":"password123","displayName":"Admin"}""")
        }.bodyAsText()
        val accessToken = """"accessToken":"([^"]+)"""".toRegex().find(registerBody)!!.groupValues[1]

        val response = client.post("/api/v1/events") {
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Acme Derby"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `register rejects a duplicate tenant slug`() = testApplication {
        application { configureTestApp(DeploymentMode.HOSTED) }

        client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantName":"Acme Racing","tenantSlug":"acme","username":"admin","password":"password123","displayName":"Admin"}""")
        }
        val response = client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantName":"Acme Racing 2","tenantSlug":"acme","username":"admin2","password":"password123","displayName":"Admin 2"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"TENANT_SLUG_TAKEN\""))
    }

    @Test
    fun `admin creates a tenant user defaulting to DIRECTOR`() = testApplication {
        application { configureTestApp(DeploymentMode.LOCAL) }

        val setupResult = authService.setupAdmin("admin", "password123", "Admin")
        val adminUser = (setupResult as io.github.raginlundf.racingmanager.application.auth.SetupResult.Success).user
        val login = authService.login("admin", "password123") as io.github.raginlundf.racingmanager.application.auth.LoginResult.Success

        val response = client.post("/api/v1/tenant/users") {
            header("Authorization", "Bearer ${login.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"username":"director","password":"password123","displayName":"Director"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"role\":\"DIRECTOR\""))

        // The new user can log in and only gets rm:user, not rm:admin.
        val newLoginBody = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"director","password":"password123"}""")
        }.bodyAsText()
        assertTrue(newLoginBody.contains("\"scopes\":[\"rm:user\"]"))

        // The tenant now has two distinct users under the same admin's tenant.
        assertEquals(adminUser.tenantId, tenantRepository.findBySlug(AuthService.LOCAL_TENANT_SLUG)!!.id)
    }

    @Test
    fun `a normal user cannot create tenant users - privilege escalation is rejected`() = testApplication {
        application { configureTestApp(DeploymentMode.LOCAL) }

        val setupResult = authService.setupAdmin("admin", "password123", "Admin") as io.github.raginlundf.racingmanager.application.auth.SetupResult.Success
        authService.createTenantUser(setupResult.user.tenantId, "director", "password123", "Director")
        val directorLogin = authService.login("director", "password123") as io.github.raginlundf.racingmanager.application.auth.LoginResult.Success

        val response = client.post("/api/v1/tenant/users") {
            header("Authorization", "Bearer ${directorLogin.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"username":"another-admin","password":"password123","displayName":"Sneaky","role":"ADMIN"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `duplicate username within the same tenant is rejected`() = testApplication {
        application { configureTestApp(DeploymentMode.LOCAL) }

        val setupResult = authService.setupAdmin("admin", "password123", "Admin") as io.github.raginlundf.racingmanager.application.auth.SetupResult.Success
        val login = authService.login("admin", "password123") as io.github.raginlundf.racingmanager.application.auth.LoginResult.Success
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

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"USERNAME_TAKEN\""))
    }

    @Test
    fun `login without a tenant slug fails closed when the username is ambiguous across tenants`() = testApplication {
        application { configureTestApp(DeploymentMode.HOSTED) }

        client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantName":"Tenant One","tenantSlug":"tenant-one","username":"admin","password":"password123","displayName":"Admin One"}""")
        }
        client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantName":"Tenant Two","tenantSlug":"tenant-two","username":"admin","password":"password123","displayName":"Admin Two"}""")
        }

        val ambiguous = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, ambiguous.status)

        val disambiguated = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","tenantSlug":"tenant-two"}""")
        }
        assertEquals(HttpStatusCode.OK, disambiguated.status)
        assertTrue(disambiguated.bodyAsText().contains("\"displayName\":\"Admin Two\""))
    }
}
