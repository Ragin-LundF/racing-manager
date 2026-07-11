package io.github.raginlundf.racingmanager.api.event

import io.github.raginlundf.racingmanager.api.configureRouting
import io.github.raginlundf.racingmanager.api.configureSerialization
import io.github.raginlundf.racingmanager.api.configureStatusPages
import io.github.raginlundf.racingmanager.infrastructure.configureWebSockets
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.knockout.KnockoutService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.spectator.SpectatorWebSocketService
import io.github.raginlundf.racingmanager.infrastructure.repositories.KnockoutRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.QualificationRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SessionRepository
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
    private val sessionRepository = SessionRepository()
    private val auditRepository = AuditRepository()
    private val eventRepository = EventRepository()
    private val passwordHasher = PasswordHasher()
    private val authService = AuthService(userRepository, sessionRepository, auditRepository, passwordHasher)
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

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    private fun Application.configureTestApp() {
        configureSerialization()
        configureStatusPages()
        configureWebSockets()
        configureRouting(authService, eventService, participantService, heatService, qualificationService, knockoutService, spectatorService, eventRepository, spectatorWebSocketService)
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
        val sid = extractSessionId(loginResponse.bodyAsText())

        val response = client.post("/api/v1/events") {
            header("X-Session-Id", sid)
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
        val sid = extractSessionId(loginResponse.bodyAsText())

        val response = client.get("/api/v1/events") {
            header("X-Session-Id", sid)
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
        val sid = extractSessionId(loginResponse.bodyAsText())

        client.post("/api/v1/events") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Event 1"}""")
        }
        client.post("/api/v1/events") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Event 2"}""")
        }

        val response = client.get("/api/v1/events") {
            header("X-Session-Id", sid)
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
        val sid = extractSessionId(loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Find Me"}""")
        }
        val eventId = extractEventId(createResponse.bodyAsText())

        val response = client.get("/api/v1/events/$eventId") {
            header("X-Session-Id", sid)
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
        val sid = extractSessionId(loginResponse.bodyAsText())

        val response = client.get("/api/v1/events/00000000-0000-0000-0000-000000000000") {
            header("X-Session-Id", sid)
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
        val sid = extractSessionId(loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Original"}""")
        }
        val eventId = extractEventId(createResponse.bodyAsText())

        val response = client.put("/api/v1/events/$eventId") {
            header("X-Session-Id", sid)
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
        val sid = extractSessionId(loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Original"}""")
        }
        val eventId = extractEventId(createResponse.bodyAsText())

        val response = client.put("/api/v1/events/$eventId") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Updated","expectedVersion":999}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"VERSION_CONFLICT\""))
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
        val sid = extractSessionId(loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"To Activate"}""")
        }
        val eventId = extractEventId(createResponse.bodyAsText())

        val response = client.post("/api/v1/events/$eventId/activate") {
            header("X-Session-Id", sid)
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
        val sid = extractSessionId(loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"To Archive"}""")
        }
        val eventId = extractEventId(createResponse.bodyAsText())

        client.post("/api/v1/events/$eventId/activate") {
            header("X-Session-Id", sid)
        }

        val response = client.post("/api/v1/events/$eventId/archive") {
            header("X-Session-Id", sid)
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
        val sid = extractSessionId(loginResponse.bodyAsText())

        val createResponse = client.post("/api/v1/events") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Draft Only"}""")
        }
        val eventId = extractEventId(createResponse.bodyAsText())

        val response = client.post("/api/v1/events/$eventId/archive") {
            header("X-Session-Id", sid)
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    private fun extractSessionId(body: String): String {
        val regex = """"sessionId":"([^"]+)"""".toRegex()
        return regex.find(body)?.groupValues?.get(1)
            ?: throw IllegalStateException("Could not extract sessionId from: $body")
    }

    private fun extractEventId(body: String): String {
        val regex = """"id":"([^"]+)"""".toRegex()
        return regex.find(body)?.groupValues?.get(1)
            ?: throw IllegalStateException("Could not extract event id from: $body")
    }
}
