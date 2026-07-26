package io.github.raginlundf.racingmanager.api.event

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
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
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

class EventRoutesTest {

    private val userRepository = UserRepository()
    private val jwtKeyProvider = LocalJwtKeyProvider(repository = SigningKeyRepository())
    private val jwtService = JwtService(keyProvider = jwtKeyProvider)
    private val auditRepository = AuditRepository()
    private val eventRepository = EventRepository()
    private val passwordHasher = PasswordHasher()
    private val authService = AuthService(
        userRepository = userRepository,
        tenantRepository = TenantRepository(),
        membershipRepository = MembershipRepository(),
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
            override fun getConnection() = throw SQLException("not used in event test")
            override fun getConnection(username: String?, password: String?) = throw SQLException(
                "not used in event test"
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

    @Test
    fun `create event returns 201`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sid = extractAccessToken(body = loginResponse.bodyAsText())

        val response = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }

        assertEquals(expected = HttpStatusCode.Created, actual = response.status)
        val body = response.bodyAsText()
        assertTrue(actual = body.contains("\"name\":\"Test Event\""))
        assertTrue(actual = body.contains("\"status\":\"ACTIVE\""))
        assertTrue(actual = body.contains("\"version\":0"))
    }

    @Test
    fun `create event persists the track length`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sid = extractAccessToken(body = loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event","trackLength":250}""")
        }
        assertEquals(expected = HttpStatusCode.Created, actual = createResponse.status)
        assertTrue(actual = createResponse.bodyAsText().contains("\"trackLength\":250"))

        val id = extractEventId(body = createResponse.bodyAsText())
        val getResponse = client.get("/api/v1/events/$id") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = getResponse.status)
        assertTrue(actual = getResponse.bodyAsText().contains("\"trackLength\":250"))
    }

    @Test
    fun `create event treats a non-positive track length as unset`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sid = extractAccessToken(body = loginResponse.bodyAsText())

        val response = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event","trackLength":0}""")
        }

        assertEquals(expected = HttpStatusCode.Created, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"trackLength\":null"))
    }

    @Test
    fun `create event returns 401 without session`() = testApplication {
        application { configureTestApp() }

        val response = client.post("/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }

        assertEquals(expected = HttpStatusCode.Unauthorized, actual = response.status)
    }

    @Test
    fun `list events returns empty list initially`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sid = extractAccessToken(body = loginResponse.bodyAsText())

        val response = client.get("/api/v1/events") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertEquals(expected = "[]", actual = response.bodyAsText())
    }

    @Test
    fun `list events returns created events`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sid = extractAccessToken(body = loginResponse.bodyAsText())

        client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Event 1"}""")
        }
        client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Event 2"}""")
        }

        val response = client.get("/api/v1/events") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        val body = response.bodyAsText()
        assertTrue(actual = body.contains("\"name\":\"Event 1\""))
        assertTrue(actual = body.contains("\"name\":\"Event 2\""))
    }

    @Test
    fun `get event by id returns event`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sid = extractAccessToken(body = loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Find Me"}""")
        }
        val eventId = extractEventId(body = createResponse.bodyAsText())

        val response = client.get("/api/v1/events/$eventId") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"name\":\"Find Me\""))
    }

    @Test
    fun `get event by id returns 404 for unknown`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sid = extractAccessToken(body = loginResponse.bodyAsText())

        val response = client.get("/api/v1/events/00000000-0000-0000-0000-000000000000") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(expected = HttpStatusCode.NotFound, actual = response.status)
    }

    @Test
    fun `update event returns updated event`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sid = extractAccessToken(body = loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Original"}""")
        }
        val eventId = extractEventId(body = createResponse.bodyAsText())

        val response = client.put("/api/v1/events/$eventId") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Updated","expectedVersion":0}""")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"name\":\"Updated\""))
        assertTrue(actual = response.bodyAsText().contains("\"version\":1"))
    }

    @Test
    fun `update event with wrong version returns 409`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sid = extractAccessToken(body = loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Original"}""")
        }
        val eventId = extractEventId(body = createResponse.bodyAsText())

        val response = client.put("/api/v1/events/$eventId") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Updated","expectedVersion":999}""")
        }

        assertEquals(expected = HttpStatusCode.Conflict, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"code\":\"VERSION_CONFLICT\""))
    }

    @Test
    fun `updating an event checked out to a local instance returns 423`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sid = extractAccessToken(body = loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Checked Out"}""")
        }
        val eventId = extractEventId(body = createResponse.bodyAsText())

        // Simulate the event having been checked out via a bootstrap package
        // (Slice H's LocalPackageService.issue locks it for sync).
        val tenantId = """"tenantId":"([^"]+)"""".toRegex().find(loginResponse.bodyAsText())!!.groupValues[1]
        localPackageService.issue(
            tenantId = UUID.fromString(tenantId),
            eventIds = listOf(UUID.fromString(eventId))
        )

        val response = client.put("/api/v1/events/$eventId") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Should Not Apply","expectedVersion":0}""")
        }

        assertEquals(expected = HttpStatusCode.Locked, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"code\":\"EVENT_LOCKED_FOR_SYNC\""))
    }

    @Test
    fun `activate event returns activated event`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sid = extractAccessToken(body = loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"To Activate"}""")
        }
        // A second event takes over as the active one, returning the first to DRAFT.
        client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Takes Over"}""")
        }
        val eventId = extractEventId(body = createResponse.bodyAsText())

        val response = client.post("/api/v1/events/$eventId/activate") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"status\":\"ACTIVE\""))
    }

    @Test
    fun `archive event returns archived event`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sid = extractAccessToken(body = loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"To Archive"}""")
        }
        val eventId = extractEventId(body = createResponse.bodyAsText())

        client.post("/api/v1/events/$eventId/activate") {
            header("Authorization", "Bearer $sid")
        }

        val response = client.post("/api/v1/events/$eventId/archive") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"status\":\"ARCHIVED\""))
    }

    @Test
    fun `archive draft event returns 409`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }
        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sid = extractAccessToken(body = loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Draft Only"}""")
        }
        // A second event takes over as the active one, returning the first to DRAFT.
        client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Takes Over"}""")
        }
        val eventId = extractEventId(body = createResponse.bodyAsText())

        val response = client.post("/api/v1/events/$eventId/archive") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(expected = HttpStatusCode.Conflict, actual = response.status)
    }

    private fun extractAccessToken(body: String): String {
        val regex = """"accessToken":"([^"]+)"""".toRegex()
        return regex.find(body)?.groupValues?.get(1)
            ?: error("Could not extract sessionId from: $body")
    }

    private fun extractEventId(body: String): String {
        val regex = """"id":"([^"]+)"""".toRegex()
        return regex.find(body)?.groupValues?.get(1)
            ?: error("Could not extract event id from: $body")
    }
}
