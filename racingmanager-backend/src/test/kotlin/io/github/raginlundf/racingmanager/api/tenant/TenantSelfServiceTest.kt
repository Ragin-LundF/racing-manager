package io.github.raginlundf.racingmanager.api.tenant

import io.github.raginlundf.racingmanager.api.configureRouting
import io.github.raginlundf.racingmanager.api.configureSerialization
import io.github.raginlundf.racingmanager.api.configureStatusPages
import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.bootstrap.LocalPackageService
import io.github.raginlundf.racingmanager.application.sync.SyncService
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.LoginResult
import io.github.raginlundf.racingmanager.application.auth.SetupResult
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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
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

/** Slice E.1/E.4: tenant admin self-service — `rm:admin`-only, always scoped
    to the caller's own tenant. */
class TenantSelfServiceTest {

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
            override fun getConnection() = throw java.sql.SQLException("not used in tenant self-service test")
            override fun getConnection(username: String?, password: String?) = throw java.sql.SQLException("not used in tenant self-service test")
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
        configureRouting(authService, jwtService, eventService, participantService, heatService, qualificationService, knockoutService, resultsService, spectatorService, eventRepository, spectatorWebSocketService, auditService, diagnosticsService, DeploymentMode.LOCAL, spectatorExchangeCodeRepository, localPackageService, syncService, io.github.raginlundf.racingmanager.api.testRaceDeviceGateway(), io.github.raginlundf.racingmanager.api.testRaceDeviceSettingsRepository())
    }

    private fun adminLogin(): LoginResult.Success {
        authService.setupAdmin("admin", "password123", "Admin")
        return authService.login("admin", "password123") as LoginResult.Success
    }

    @Test
    fun `admin can view and update their own tenant`() = testApplication {
        application { configureTestApp() }
        val admin = adminLogin()

        val getResponse = client.get("/api/v1/tenant") {
            header("Authorization", "Bearer ${admin.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertTrue(getResponse.bodyAsText().contains("\"displayName\":\"Local\""))

        val putResponse = client.put("/api/v1/tenant") {
            header("Authorization", "Bearer ${admin.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"Renamed Club","settings":"{\"locale\":\"de\"}"}""")
        }
        assertEquals(HttpStatusCode.OK, putResponse.status)
        assertTrue(putResponse.bodyAsText().contains("\"displayName\":\"Renamed Club\""))
    }

    @Test
    fun `a normal user is forbidden from viewing tenant settings`() = testApplication {
        application { configureTestApp() }
        val admin = adminLogin()
        authService.createTenantUser(admin.user.tenantId, "director", "password123", "Director")
        val directorLogin = authService.login("director", "password123") as LoginResult.Success

        val response = client.get("/api/v1/tenant") {
            header("Authorization", "Bearer ${directorLogin.accessToken}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `admin lists tenant users including newly created ones`() = testApplication {
        application { configureTestApp() }
        val admin = adminLogin()
        client.post("/api/v1/tenant/users") {
            header("Authorization", "Bearer ${admin.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"username":"director","password":"password123","displayName":"Director"}""")
        }

        val response = client.get("/api/v1/tenant/users") {
            header("Authorization", "Bearer ${admin.accessToken}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"username\":\"admin\""))
        assertTrue(body.contains("\"username\":\"director\""))
    }

    @Test
    fun `admin promotes a director to admin via role update`() = testApplication {
        application { configureTestApp() }
        val admin = adminLogin()
        val createBody = client.post("/api/v1/tenant/users") {
            header("Authorization", "Bearer ${admin.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"username":"director","password":"password123","displayName":"Director"}""")
        }.bodyAsText()
        val newUserId = """"userId":"([^"]+)"""".toRegex().find(createBody)!!.groupValues[1]

        val putResponse = client.put("/api/v1/tenant/users/$newUserId") {
            header("Authorization", "Bearer ${admin.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"role":"ADMIN"}""")
        }
        assertEquals(HttpStatusCode.OK, putResponse.status)
        assertTrue(putResponse.bodyAsText().contains("\"role\":\"ADMIN\""))

        val promotedLogin = authService.login("director", "password123") as LoginResult.Success
        assertEquals(setOf("rm:admin"), promotedLogin.scopes)
    }

    @Test
    fun `admin deactivates a user who can no longer log in`() = testApplication {
        application { configureTestApp() }
        val admin = adminLogin()
        val createBody = client.post("/api/v1/tenant/users") {
            header("Authorization", "Bearer ${admin.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"username":"director","password":"password123","displayName":"Director"}""")
        }.bodyAsText()
        val newUserId = """"userId":"([^"]+)"""".toRegex().find(createBody)!!.groupValues[1]

        val deleteResponse = client.delete("/api/v1/tenant/users/$newUserId") {
            header("Authorization", "Bearer ${admin.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, deleteResponse.status)
        assertTrue(deleteResponse.bodyAsText().contains("\"status\":\"DISABLED\""))

        val loginAttempt = authService.login("director", "password123")
        assertEquals(LoginResult.InvalidCredentials, loginAttempt)
    }

    @Test
    fun `a normal user cannot list or manage tenant users`() = testApplication {
        application { configureTestApp() }
        val admin = adminLogin()
        authService.createTenantUser(admin.user.tenantId, "director", "password123", "Director")
        val directorLogin = authService.login("director", "password123") as LoginResult.Success

        val listResponse = client.get("/api/v1/tenant/users") {
            header("Authorization", "Bearer ${directorLogin.accessToken}")
        }
        assertEquals(HttpStatusCode.Forbidden, listResponse.status)
    }
}
