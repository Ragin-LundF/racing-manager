package io.github.raginlundf.racingmanager.api.auth

import io.github.raginlundf.racingmanager.api.configureRouting
import io.github.raginlundf.racingmanager.api.configureSerialization
import io.github.raginlundf.racingmanager.api.configureStatusPages
import io.github.raginlundf.racingmanager.api.testRaceDeviceGateway
import io.github.raginlundf.racingmanager.api.testRaceDeviceSettingsRepository
import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.auth.AuthService
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
import java.io.PrintWriter
import java.sql.SQLException
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRoutesTest {

    private val userRepository = UserRepository()
    private val auditRepository = AuditRepository()
    private val passwordHasher = PasswordHasher()
    private val jwtKeyProvider = LocalJwtKeyProvider(repository = SigningKeyRepository())
    private val jwtService = JwtService(keyProvider = jwtKeyProvider)
    private val authService = AuthService(
        userRepository = userRepository,
        tenantRepository = TenantRepository(),
        membershipRepository = MembershipRepository(),
        refreshTokenRepository = RefreshTokenRepository(),
        auditRepository = auditRepository,
        passwordHasher = passwordHasher,
        jwtService = jwtService,
    )
    private val eventRepository = EventRepository()
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
        tenantRepository = TenantRepository(),
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
            override fun getConnection() = throw SQLException("not used in auth test")
            override fun getConnection(username: String?, password: String?) = throw SQLException(
                "not used in auth test"
            )

            override fun getLogWriter() = null

            @Suppress("EmptyFunctionBlock")
            override fun setLogWriter(out: PrintWriter?) {
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

    @Test
    fun `setup-status returns firstRun true initially`() = testApplication {
        application { configureTestApp() }

        val response = client.get("/api/v1/auth/setup-status")

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        val body = response.bodyAsText()
        assertTrue(actual = body.contains("\"firstRun\":true"))
        assertTrue(actual = body.contains("\"mode\":\"LOCAL\""))
    }

    @Test
    fun `setup-status reports hosted mode`() = testApplication {
        application { configureTestApp(deploymentMode = DeploymentMode.HOSTED) }

        val response = client.get("/api/v1/auth/setup-status")

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"mode\":\"HOSTED\""))
    }

    @Test
    fun `setup returns 403 in hosted mode`() = testApplication {
        application { configureTestApp(deploymentMode = DeploymentMode.HOSTED) }

        val response = client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"code\":\"NOT_LOCAL_MODE\""))
    }

    @Test
    fun `setup creates admin and returns 201`() = testApplication {
        application { configureTestApp() }

        val response = client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }

        assertEquals(expected = HttpStatusCode.Created, actual = response.status)
        val body = response.bodyAsText()
        assertTrue(actual = body.contains("\"username\":\"admin\""))
        assertTrue(actual = body.contains("\"displayName\":\"Admin User\""))
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

        assertEquals(expected = HttpStatusCode.Conflict, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"code\":\"ALREADY_SETUP\""))
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

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        val body = response.bodyAsText()
        assertTrue(actual = body.contains("\"accessToken\""))
        assertTrue(actual = body.contains("\"refreshToken\""))
        assertTrue(actual = body.contains("\"username\":\"admin\""))
        assertTrue(actual = body.contains("\"role\":\"ADMIN\""))
        assertTrue(actual = body.contains("\"scopes\":[\"rm:admin\"]"))
    }

    @Test
    fun `login for a deactivated tenant returns 401 TENANT_DISABLED`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        authService.deactivateTenant(
            tenantId = AuthService.LOCAL_TENANT_ID,
            supervisorId = java.util.UUID.randomUUID()
        )

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }

        assertEquals(expected = HttpStatusCode.Unauthorized, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"code\":\"TENANT_DISABLED\""))
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

        assertEquals(expected = HttpStatusCode.Unauthorized, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"code\":\"INVALID_CREDENTIALS\""))
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
        val accessToken = loginResponse.bodyAsText().extractField(field = "accessToken")

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
        val refreshToken = loginResponse.bodyAsText().extractField(field = "refreshToken")

        val response = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"accessToken\""))
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
        val refreshToken = loginResponse.bodyAsText().extractField(field = "refreshToken")

        val logoutResponse = client.post("/api/v1/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        assertEquals(expected = HttpStatusCode.NoContent, actual = logoutResponse.status)

        val refreshResponse = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        assertEquals(expected = HttpStatusCode.Unauthorized, actual = refreshResponse.status)
    }

    @Test
    fun `setup-status returns firstRun false after setup`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }

        val response = client.get("/api/v1/auth/setup-status")

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"firstRun\":false"))
    }

    private fun Application.configureTestApp(deploymentMode: DeploymentMode = DeploymentMode.LOCAL) {
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
            deploymentMode = deploymentMode,
            spectatorExchangeCodeRepository = spectatorExchangeCodeRepository,
            localPackageService = localPackageService,
            syncService = syncService,
            raceDeviceGateway = testRaceDeviceGateway(),
            raceDeviceSettingsRepository = testRaceDeviceSettingsRepository()
        )
    }

    private fun String.extractField(field: String): String {
        val regex = """"$field":"([^"]+)"""".toRegex()
        return regex.find(this)?.groupValues?.get(1)
            ?: error("Could not extract $field from: $this")
    }
}
