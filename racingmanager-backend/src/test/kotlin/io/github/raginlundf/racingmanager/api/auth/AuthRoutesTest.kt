package io.github.raginlundf.racingmanager.api.auth

import io.github.raginlundf.racingmanager.api.configureRouting
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.api.configureSerialization
import io.github.raginlundf.racingmanager.api.configureStatusPages
import io.github.raginlundf.racingmanager.application.diagnostics.DiagnosticsService
import io.github.raginlundf.racingmanager.infrastructure.configureWebSockets
import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.bootstrap.LocalPackageService
import io.github.raginlundf.racingmanager.application.sync.SyncService
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.knockout.KnockoutService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.application.results.ResultsService
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.spectator.SpectatorWebSocketService
import io.github.raginlundf.racingmanager.infrastructure.repositories.KnockoutRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.LocalInstanceRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ImportedPackageRepository
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

class AuthRoutesTest {

    private val userRepository = UserRepository()
    private val auditRepository = AuditRepository()
    private val passwordHasher = PasswordHasher()
    private val jwtKeyProvider = LocalJwtKeyProvider(SigningKeyRepository())
    private val jwtService = JwtService(jwtKeyProvider)
    private val authService = AuthService(
        userRepository,
        TenantRepository(),
        MembershipRepository(),
        RefreshTokenRepository(),
        auditRepository,
        passwordHasher,
        jwtService,
    )
    private val eventRepository = EventRepository()
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
    private val localPackageService = LocalPackageService(eventRepository, participantRepository, TenantRepository(), importedPackageRepository, localInstanceRepository, jwtKeyProvider)
    private val pairingCodeRepository = PairingCodeRepository()
    private val pairedInstanceRepository = PairedInstanceRepository()
    private val syncedResultRepository = SyncedResultRepository()
    private val syncService = SyncService(pairingCodeRepository, pairedInstanceRepository, syncedResultRepository, eventRepository, auditRepository)
    private val resultsService = ResultsService(eventRepository, participantRepository, heatRepository, qualificationRepository, knockoutRepository, auditRepository)
    private val auditService = AuditService(auditRepository)
    private val diagnosticsService = DiagnosticsService(
        object : javax.sql.DataSource {
            override fun getConnection() = throw java.sql.SQLException("not used in auth test")
            override fun getConnection(username: String?, password: String?) = throw java.sql.SQLException("not used in auth test")
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

    @Test
    fun `setup-status returns firstRun true initially`() = testApplication {
        application { configureTestApp() }

        val response = client.get("/api/v1/auth/setup-status")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"firstRun\":true"))
        assertTrue(body.contains("\"mode\":\"LOCAL\""))
    }

    @Test
    fun `setup-status reports hosted mode`() = testApplication {
        application { configureTestApp(DeploymentMode.HOSTED) }

        val response = client.get("/api/v1/auth/setup-status")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"mode\":\"HOSTED\""))
    }

    @Test
    fun `setup returns 403 in hosted mode`() = testApplication {
        application { configureTestApp(DeploymentMode.HOSTED) }

        val response = client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"NOT_LOCAL_MODE\""))
    }

    @Test
    fun `setup creates admin and returns 201`() = testApplication {
        application { configureTestApp() }

        val response = client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"username\":\"admin\""))
        assertTrue(body.contains("\"displayName\":\"Admin User\""))
    }

    @Test
    fun `setup returns 409 on second call`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }

        val response = client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin2","password":"otherpass","displayName":"Other"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"ALREADY_SETUP\""))
    }

    @Test
    fun `login with valid credentials returns 200 and tokens`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"accessToken\""))
        assertTrue(body.contains("\"refreshToken\""))
        assertTrue(body.contains("\"username\":\"admin\""))
        assertTrue(body.contains("\"role\":\"ADMIN\""))
        assertTrue(body.contains("\"scopes\":[\"rm:admin\"]"))
    }

    @Test
    fun `login for a deactivated tenant returns 401 TENANT_DISABLED`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        authService.deactivateTenant(AuthService.LOCAL_TENANT_ID, java.util.UUID.randomUUID())

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"TENANT_DISABLED\""))
    }

    @Test
    fun `login with wrong password returns 401`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"wrongpassword"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"INVALID_CREDENTIALS\""))
    }

    @Test
    fun `session returns user info for a valid access token`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }

        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val accessToken = loginResponse.bodyAsText().extractField("accessToken")

        val response = client.get("/api/v1/auth/session") {
            header("Authorization", "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"username\":\"admin\""))
        assertTrue(body.contains("\"role\":\"ADMIN\""))
    }

    @Test
    fun `session returns 401 for missing bearer token`() = testApplication {
        application { configureTestApp() }

        val response = client.get("/api/v1/auth/session")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"MISSING_TOKEN\""))
    }

    @Test
    fun `session returns 401 for an invalid access token`() = testApplication {
        application { configureTestApp() }

        val response = client.get("/api/v1/auth/session") {
            header("Authorization", "Bearer not-a-real-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"INVALID_TOKEN\""))
    }

    @Test
    fun `refresh with a valid refresh token returns a new access token`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val refreshToken = loginResponse.bodyAsText().extractField("refreshToken")

        val response = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"accessToken\""))
    }

    @Test
    fun `logout returns 204 and revokes the refresh token`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val refreshToken = loginResponse.bodyAsText().extractField("refreshToken")

        val logoutResponse = client.post("/api/v1/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        assertEquals(HttpStatusCode.NoContent, logoutResponse.status)

        val refreshResponse = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, refreshResponse.status)
    }

    @Test
    fun `setup-status returns firstRun false after setup`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }

        val response = client.get("/api/v1/auth/setup-status")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"firstRun\":false"))
    }

    private fun Application.configureTestApp(deploymentMode: DeploymentMode = DeploymentMode.LOCAL) {
        configureSerialization()
        configureStatusPages()
        configureWebSockets()
        configureRouting(authService, jwtService, eventService, participantService, heatService, qualificationService, knockoutService, resultsService, spectatorService, eventRepository, spectatorWebSocketService, auditService, diagnosticsService, deploymentMode, spectatorExchangeCodeRepository, localPackageService, syncService)
    }

    private fun String.extractField(field: String): String {
        val regex = """"$field":"([^"]+)"""".toRegex()
        return regex.find(this)?.groupValues?.get(1)
            ?: throw IllegalStateException("Could not extract $field from: $this")
    }
}
