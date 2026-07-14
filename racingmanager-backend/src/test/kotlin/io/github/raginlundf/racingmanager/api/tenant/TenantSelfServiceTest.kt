package io.github.raginlundf.racingmanager.api.tenant

import io.github.raginlundf.racingmanager.api.configureRouting
import io.github.raginlundf.racingmanager.api.configureSerialization
import io.github.raginlundf.racingmanager.api.configureStatusPages
import io.github.raginlundf.racingmanager.api.testRaceDeviceGateway
import io.github.raginlundf.racingmanager.api.testRaceDeviceSettingsRepository
import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.LoginResult
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
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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

/** Slice E.1/E.4: tenant admin self-service — `rm:admin`-only, always scoped
to the caller's own tenant. */
class TenantSelfServiceTest {

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
            override fun getConnection() = throw SQLException("not used in tenant self-service test")
            override fun getConnection(username: String?, password: String?) = throw SQLException(
                "not used in tenant self-service test"
            )

            override fun getLogWriter() = null
            override fun setLogWriter(out: PrintWriter?) {}
            override fun setLoginTimeout(seconds: Int) {}
            override fun getLoginTimeout() = 0
            override fun <T> unwrap(iface: Class<T>?) = throw SQLException("not a wrapper")
            override fun isWrapperFor(iface: Class<*>?) = false
            override fun getParentLogger() = Logger.getLogger("")
        },
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        heatRepository = heatRepository
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

    private fun adminLogin(): LoginResult.Success {
        authService.setupAdmin(username = "admin", password = "password123", displayName = "Admin")
        return authService.login(username = "admin", password = "password123") as LoginResult.Success
    }

    @Test
    fun `admin can view and update their own tenant`() = testApplication {
        application { configureTestApp() }
        val admin = adminLogin()

        val getResponse = client.get("/api/v1/tenant") {
            header(key = "Authorization", value = "Bearer ${admin.accessToken}")
        }
        assertEquals(expected = HttpStatusCode.OK, actual = getResponse.status)
        assertTrue(actual = getResponse.bodyAsText().contains("\"displayName\":\"Local\""))

        val putResponse = client.put("/api/v1/tenant") {
            header(key = "Authorization", value = "Bearer ${admin.accessToken}")
            contentType(type = ContentType.Application.Json)
            setBody(body = """{"displayName":"Renamed Club","settings":"{\"locale\":\"de\"}"}""")
        }
        assertEquals(expected = HttpStatusCode.OK, actual = putResponse.status)
        assertTrue(actual = putResponse.bodyAsText().contains(other = "\"displayName\":\"Renamed Club\""))
    }

    @Test
    fun `a normal user is forbidden from viewing tenant settings`() = testApplication {
        application { configureTestApp() }
        val admin = adminLogin()
        authService.createTenantUser(
            tenantId = admin.user.tenantId,
            username = "director",
            password = "password123",
            displayName = "Director"
        )
        val directorLogin = authService.login(username = "director", password = "password123") as LoginResult.Success

        val response = client.get("/api/v1/tenant") {
            header(key = "Authorization", value = "Bearer ${directorLogin.accessToken}")
        }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
    }

    @Test
    fun `admin lists tenant users including newly created ones`() = testApplication {
        application { configureTestApp() }
        val admin = adminLogin()
        client.post("/api/v1/tenant/users") {
            header(key = "Authorization", value = "Bearer ${admin.accessToken}")
            contentType(type = ContentType.Application.Json)
            setBody(body = """{"username":"director","password":"password123","displayName":"Director"}""")
        }

        val response = client.get("/api/v1/tenant/users") {
            header(key = "Authorization", value = "Bearer ${admin.accessToken}")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        val body = response.bodyAsText()
        assertTrue(actual = body.contains(other = "\"username\":\"admin\""))
        assertTrue(actual = body.contains(other = "\"username\":\"director\""))
    }

    @Test
    fun `admin promotes a director to admin via role update`() = testApplication {
        application { configureTestApp() }
        val admin = adminLogin()
        val createBody = client.post("/api/v1/tenant/users") {
            header(key = "Authorization", value = "Bearer ${admin.accessToken}")
            contentType(type = ContentType.Application.Json)
            setBody(body = """{"username":"director","password":"password123","displayName":"Director"}""")
        }.bodyAsText()
        val newUserId = """"userId":"([^"]+)"""".toRegex().find(input = createBody)!!.groupValues[1]

        val putResponse = client.put("/api/v1/tenant/users/$newUserId") {
            header(key = "Authorization", value = "Bearer ${admin.accessToken}")
            contentType(type = ContentType.Application.Json)
            setBody(body = """{"role":"ADMIN"}""")
        }
        assertEquals(expected = HttpStatusCode.OK, actual = putResponse.status)
        assertTrue(actual = putResponse.bodyAsText().contains(other = "\"role\":\"ADMIN\""))

        val promotedLogin = authService.login(username = "director", password = "password123") as LoginResult.Success
        assertEquals(expected = setOf("rm:admin"), actual = promotedLogin.scopes)
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
        assertEquals(expected = HttpStatusCode.OK, actual = deleteResponse.status)
        assertTrue(actual = deleteResponse.bodyAsText().contains(other = "\"status\":\"DISABLED\""))

        val loginAttempt = authService.login(username = "director", password = "password123")
        assertEquals(expected = LoginResult.InvalidCredentials, actual = loginAttempt)
    }

    @Test
    fun `a normal user cannot list or manage tenant users`() = testApplication {
        application { configureTestApp() }
        val admin = adminLogin()
        authService.createTenantUser(
            tenantId = admin.user.tenantId,
            username = "director",
            password = "password123",
            displayName = "Director"
        )
        val directorLogin = authService.login(username = "director", password = "password123") as LoginResult.Success

        val listResponse = client.get("/api/v1/tenant/users") {
            header(key = "Authorization", value = "Bearer ${directorLogin.accessToken}")
        }
        assertEquals(expected = HttpStatusCode.Forbidden, actual = listResponse.status)
    }
}
