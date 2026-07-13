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
            override fun getConnection() = throw java.sql.SQLException("not used in supervisor test")
            override fun getConnection(username: String?, password: String?) = throw java.sql.SQLException("not used in supervisor test")
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
    fun `supervisor setup is forbidden in local mode`() = testApplication {
        application { configureTestApp(DeploymentMode.LOCAL) }

        val response = client.post("/api/v1/admin/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"root","password":"password123","displayName":"Root"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `supervisor setup succeeds once in hosted mode and rejects a second attempt`() = testApplication {
        application { configureTestApp(DeploymentMode.HOSTED) }

        val first = client.post("/api/v1/admin/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"root","password":"password123","displayName":"Root"}""")
        }
        assertEquals(HttpStatusCode.Created, first.status)

        val second = client.post("/api/v1/admin/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"root2","password":"password123","displayName":"Root2"}""")
        }
        assertEquals(HttpStatusCode.Conflict, second.status)
    }

    private suspend fun io.ktor.client.HttpClient.supervisorToken(): String {
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
        application { configureTestApp(DeploymentMode.HOSTED) }
        val supervisorToken = client.supervisorToken()
        client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantName":"Acme Racing","tenantSlug":"acme","username":"admin","password":"password123","displayName":"Admin"}""")
        }

        val response = client.get("/api/v1/admin/tenants") {
            header("Authorization", "Bearer $supervisorToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"slug\":\"acme\""))
        assertFalse(body.contains("\"slug\":\"platform\""))
    }

    @Test
    fun `supervisor can deactivate and request deletion of a tenant`() = testApplication {
        application { configureTestApp(DeploymentMode.HOSTED) }
        val supervisorToken = client.supervisorToken()
        val registerBody = client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantName":"Acme Racing","tenantSlug":"acme","username":"admin","password":"password123","displayName":"Admin"}""")
        }.bodyAsText()
        val tenantId = """"tenantId":"([^"]+)"""".toRegex().find(registerBody)!!.groupValues[1]

        val deactivateResponse = client.post("/api/v1/admin/tenants/$tenantId/deactivate") {
            header("Authorization", "Bearer $supervisorToken")
        }
        assertEquals(HttpStatusCode.OK, deactivateResponse.status)
        assertTrue(deactivateResponse.bodyAsText().contains("\"status\":\"DISABLED\""))

        val wrongConfirm = client.delete("/api/v1/admin/tenants/$tenantId") {
            header("Authorization", "Bearer $supervisorToken")
            contentType(ContentType.Application.Json)
            setBody("""{"confirmSlug":"wrong-slug"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, wrongConfirm.status)

        val deleteResponse = client.delete("/api/v1/admin/tenants/$tenantId") {
            header("Authorization", "Bearer $supervisorToken")
            contentType(ContentType.Application.Json)
            setBody("""{"confirmSlug":"acme"}""")
        }
        assertEquals(HttpStatusCode.OK, deleteResponse.status)
        assertTrue(deleteResponse.bodyAsText().contains("\"status\":\"PENDING_DELETION\""))
    }

    @Test
    fun `a tenant admin token is forbidden from supervisor routes`() = testApplication {
        application { configureTestApp(DeploymentMode.HOSTED) }
        client.supervisorToken()
        val registerBody = client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"tenantName":"Acme Racing","tenantSlug":"acme","username":"admin","password":"password123","displayName":"Admin"}""")
        }.bodyAsText()
        val adminAccessToken = """"accessToken":"([^"]+)"""".toRegex().find(registerBody)!!.groupValues[1]

        val response = client.get("/api/v1/admin/tenants") {
            header("Authorization", "Bearer $adminAccessToken")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `a supervisor token cannot access ordinary race-data routes`() = testApplication {
        application { configureTestApp(DeploymentMode.HOSTED) }
        val supervisorToken = client.supervisorToken()

        val response = client.get("/api/v1/events") {
            header("Authorization", "Bearer $supervisorToken")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
