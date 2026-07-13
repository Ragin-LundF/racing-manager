package io.github.raginlundf.racingmanager.api.event

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
import io.github.raginlundf.racingmanager.infrastructure.repositories.PairedInstanceRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.PairingCodeRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.QualificationRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SpectatorExchangeCodeRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SyncedResultRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.TenantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.MembershipRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.RefreshTokenRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.security.LocalJwtKeyProvider
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventRoutesTest {

    private val userRepository = UserRepository()
    private val jwtKeyProvider = LocalJwtKeyProvider(SigningKeyRepository())
    private val jwtService = JwtService(jwtKeyProvider)
    private val auditRepository = AuditRepository()
    private val eventRepository = EventRepository()
    private val passwordHasher = PasswordHasher()
    private val authService = AuthService(userRepository, TenantRepository(), MembershipRepository(), RefreshTokenRepository(), auditRepository, passwordHasher, jwtService)
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
            override fun getConnection() = throw java.sql.SQLException("not used in event test")
            override fun getConnection(username: String?, password: String?) = throw java.sql.SQLException("not used in event test")
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
        val sid = extractAccessToken(loginResponse.bodyAsText())

        val response = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"name\":\"Test Event\""))
        assertTrue(body.contains("\"status\":\"DRAFT\""))
        assertTrue(body.contains("\"version\":0"))
    }

    @Test
    fun `create event returns 401 without session`() = testApplication {
        application { configureTestApp() }

        val response = client.post("/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
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
        val sid = extractAccessToken(loginResponse.bodyAsText())

        val response = client.get("/api/v1/events") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
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
        val sid = extractAccessToken(loginResponse.bodyAsText())

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

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"name\":\"Event 1\""))
        assertTrue(body.contains("\"name\":\"Event 2\""))
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
        val sid = extractAccessToken(loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Find Me"}""")
        }
        val eventId = extractEventId(createResponse.bodyAsText())

        val response = client.get("/api/v1/events/$eventId") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"name\":\"Find Me\""))
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
        val sid = extractAccessToken(loginResponse.bodyAsText())

        val response = client.get("/api/v1/events/00000000-0000-0000-0000-000000000000") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
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
        val sid = extractAccessToken(loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Original"}""")
        }
        val eventId = extractEventId(createResponse.bodyAsText())

        val response = client.put("/api/v1/events/$eventId") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Updated","expectedVersion":0}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"name\":\"Updated\""))
        assertTrue(response.bodyAsText().contains("\"version\":1"))
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
        val sid = extractAccessToken(loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Original"}""")
        }
        val eventId = extractEventId(createResponse.bodyAsText())

        val response = client.put("/api/v1/events/$eventId") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Updated","expectedVersion":999}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"VERSION_CONFLICT\""))
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
        val sid = extractAccessToken(loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Checked Out"}""")
        }
        val eventId = extractEventId(createResponse.bodyAsText())

        // Simulate the event having been checked out via a bootstrap package
        // (Slice H's LocalPackageService.issue locks it for sync).
        val tenantId = """"tenantId":"([^"]+)"""".toRegex().find(loginResponse.bodyAsText())!!.groupValues[1]
        localPackageService.issue(java.util.UUID.fromString(tenantId), listOf(java.util.UUID.fromString(eventId)))

        val response = client.put("/api/v1/events/$eventId") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Should Not Apply","expectedVersion":0}""")
        }

        assertEquals(HttpStatusCode.Locked, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"EVENT_LOCKED_FOR_SYNC\""))
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
        val sid = extractAccessToken(loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"To Activate"}""")
        }
        val eventId = extractEventId(createResponse.bodyAsText())

        val response = client.post("/api/v1/events/$eventId/activate") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ACTIVE\""))
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
        val sid = extractAccessToken(loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"To Archive"}""")
        }
        val eventId = extractEventId(createResponse.bodyAsText())

        client.post("/api/v1/events/$eventId/activate") {
            header("Authorization", "Bearer $sid")
        }

        val response = client.post("/api/v1/events/$eventId/archive") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ARCHIVED\""))
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
        val sid = extractAccessToken(loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Draft Only"}""")
        }
        val eventId = extractEventId(createResponse.bodyAsText())

        val response = client.post("/api/v1/events/$eventId/archive") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    private fun extractAccessToken(body: String): String {
        val regex = """"accessToken":"([^"]+)"""".toRegex()
        return regex.find(body)?.groupValues?.get(1)
            ?: throw IllegalStateException("Could not extract sessionId from: $body")
    }

    private fun extractEventId(body: String): String {
        val regex = """"id":"([^"]+)"""".toRegex()
        return regex.find(body)?.groupValues?.get(1)
            ?: throw IllegalStateException("Could not extract event id from: $body")
    }
}
