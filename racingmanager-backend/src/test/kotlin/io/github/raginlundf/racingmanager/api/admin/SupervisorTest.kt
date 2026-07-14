package io.github.raginlundf.racingmanager.api.admin

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
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Slice E.2/E.4: hosted-platform supervisor bootstrap and cross-tenant
    lifecycle management, without gaining ordinary race-data access. */
class SupervisorTest {

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
            override fun getConnection() = throw SQLException("not used in supervisor test")
            override fun getConnection(username: String?, password: String?) = throw SQLException(
                "not used in supervisor test"
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
            raceDeviceGateway = io.github.raginlundf.racingmanager.api.testRaceDeviceGateway(),
            raceDeviceSettingsRepository = io.github.raginlundf.racingmanager.api.testRaceDeviceSettingsRepository()
        )
    }

    @Test
    fun `supervisor setup is forbidden in local mode`() = testApplication {
        application { configureTestApp(mode = DeploymentMode.LOCAL) }

        val response = client.post("/api/v1/admin/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"root","password":"password123","displayName":"Root"}""")
        }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
    }

    @Test
    fun `supervisor setup succeeds once in hosted mode and rejects a second attempt`() = testApplication {
        application { configureTestApp(mode = DeploymentMode.HOSTED) }

        val first = client.post("/api/v1/admin/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"root","password":"password123","displayName":"Root"}""")
        }
        assertEquals(expected = HttpStatusCode.Created, actual = first.status)

        val second = client.post("/api/v1/admin/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"root2","password":"password123","displayName":"Root2"}""")
        }
        assertEquals(expected = HttpStatusCode.Conflict, actual = second.status)
    }

    private suspend fun HttpClient.supervisorToken(): String {
        post("/api/v1/admin/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"root","password":"password123","displayName":"Root"}""")
        }
        val loginBody = post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"root","password":"password123"}""")
        }.bodyAsText()
        return """"accessToken":"([^"]+)"""".toRegex().find(loginBody)!!.groupValues[1]
    }

    @Test
    fun `supervisor lists tenant metadata across tenants without seeing the platform tenant`() = testApplication {
        application { configureTestApp(mode = DeploymentMode.HOSTED) }
        val supervisorToken = client.supervisorToken()
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

        val response = client.get("/api/v1/admin/tenants") {
            header("Authorization", "Bearer $supervisorToken")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        val body = response.bodyAsText()
        assertTrue(actual = body.contains("\"slug\":\"acme\""))
        assertFalse(actual = body.contains("\"slug\":\"platform\""))
    }

    @Test
    fun `supervisor can deactivate and request deletion of a tenant`() = testApplication {
        application { configureTestApp(mode = DeploymentMode.HOSTED) }
        val supervisorToken = client.supervisorToken()
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
        val tenantId = """"tenantId":"([^"]+)"""".toRegex().find(registerBody)!!.groupValues[1]

        val deactivateResponse = client.post("/api/v1/admin/tenants/$tenantId/deactivate") {
            header("Authorization", "Bearer $supervisorToken")
        }
        assertEquals(expected = HttpStatusCode.OK, actual = deactivateResponse.status)
        assertTrue(actual = deactivateResponse.bodyAsText().contains("\"status\":\"DISABLED\""))

        val wrongConfirm = client.delete("/api/v1/admin/tenants/$tenantId") {
            header("Authorization", "Bearer $supervisorToken")
            contentType(ContentType.Application.Json)
            setBody("""{"confirmSlug":"wrong-slug"}""")
        }
        assertEquals(expected = HttpStatusCode.BadRequest, actual = wrongConfirm.status)

        val deleteResponse = client.delete("/api/v1/admin/tenants/$tenantId") {
            header("Authorization", "Bearer $supervisorToken")
            contentType(ContentType.Application.Json)
            setBody("""{"confirmSlug":"acme"}""")
        }
        assertEquals(expected = HttpStatusCode.OK, actual = deleteResponse.status)
        assertTrue(actual = deleteResponse.bodyAsText().contains("\"status\":\"PENDING_DELETION\""))
    }

    @Test
    fun `a tenant admin token is forbidden from supervisor routes`() = testApplication {
        application { configureTestApp(mode = DeploymentMode.HOSTED) }
        client.supervisorToken()
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
        val adminAccessToken = """"accessToken":"([^"]+)"""".toRegex().find(registerBody)!!.groupValues[1]

        val response = client.get("/api/v1/admin/tenants") {
            header("Authorization", "Bearer $adminAccessToken")
        }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
    }

    @Test
    fun `a supervisor token cannot access ordinary race-data routes`() = testApplication {
        application { configureTestApp(mode = DeploymentMode.HOSTED) }
        val supervisorToken = client.supervisorToken()

        val response = client.get("/api/v1/events") {
            header("Authorization", "Bearer $supervisorToken")
        }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
    }
}
