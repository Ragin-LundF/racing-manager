package io.github.raginlundf.racingmanager.api.participant

import io.github.raginlundf.racingmanager.api.configureRouting
import io.github.raginlundf.racingmanager.api.configureSerialization
import io.github.raginlundf.racingmanager.api.configureStatusPages
import io.github.raginlundf.racingmanager.application.diagnostics.DiagnosticsService
import io.github.raginlundf.racingmanager.infrastructure.configureWebSockets
import io.github.raginlundf.racingmanager.application.audit.AuditService
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

class ParticipantRoutesTest {

    private val userRepository = UserRepository()
    private val sessionRepository = SessionRepository()
    private val auditRepository = AuditRepository()
    private val eventRepository = EventRepository()
    private val participantRepository = ParticipantRepository()
    private val passwordHasher = PasswordHasher()
    private val authService = AuthService(userRepository, sessionRepository, auditRepository, passwordHasher)
    private val eventService = EventService(eventRepository, ParticipantRepository(), auditRepository)
    private val participantService = ParticipantService(participantRepository, eventRepository, auditRepository)
    private val heatRepository = HeatRepository()
    private val heatService = HeatService(heatRepository, eventRepository, participantRepository, auditRepository)
    private val qualificationRepository = QualificationRepository()
    private val qualificationService = QualificationService(qualificationRepository, heatRepository, eventRepository, participantRepository, auditRepository)
    private val knockoutRepository = KnockoutRepository()
    private val knockoutService = KnockoutService(knockoutRepository, heatRepository, eventRepository, participantRepository, qualificationRepository, auditRepository)
    private val spectatorService = SpectatorService(eventRepository, heatRepository, participantRepository, qualificationRepository, knockoutRepository)
    private val spectatorWebSocketService = SpectatorWebSocketService(spectatorService, heatRepository, heatService.events)
    private val resultsService = ResultsService(eventRepository, participantRepository, heatRepository, qualificationRepository, knockoutRepository, auditRepository)
    private val auditService = AuditService(auditRepository)
    private val diagnosticsService = DiagnosticsService(
        object : javax.sql.DataSource {
            override fun getConnection() = throw java.sql.SQLException("not used in participant test")
            override fun getConnection(username: String?, password: String?) = throw java.sql.SQLException("not used in participant test")
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
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    private fun Application.configureTestApp() {
        configureSerialization()
        configureStatusPages()
        configureWebSockets()
        configureRouting(authService, eventService, participantService, heatService, qualificationService, knockoutService, resultsService, spectatorService, eventRepository, spectatorWebSocketService, auditService, diagnosticsService)
    }

    private fun String.extractField(field: String): String {
        val regex = """"$field":"([^"]+)"""".toRegex()
        return regex.find(this)?.groupValues?.get(1)
            ?: throw IllegalStateException("Could not extract $field from: $this")
    }

    @Test
    fun `create participant returns 201`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin"}""")
        }
        val loginBody = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }.bodyAsText()
        val sid = loginBody.extractField("sessionId")

        val createEventBody = client.post("/api/v1/events") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }.bodyAsText()
        val eventId = createEventBody.extractField("id")

        client.post("/api/v1/events/$eventId/activate") {
            header("X-Session-Id", sid)
        }

        val response = client.post("/api/v1/events/$eventId/participants") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"startNumber":1,"firstName":"John","lastName":"Doe"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"startNumber\":1"))
        assertTrue(body.contains("\"firstName\":\"John\""))
        assertTrue(body.contains("\"status\":\"ACTIVE\""))
    }

    @Test
    fun `create participant returns 409 for duplicate start number`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin"}""")
        }
        val loginBody = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }.bodyAsText()
        val sid = loginBody.extractField("sessionId")

        val createEventBody = client.post("/api/v1/events") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }.bodyAsText()
        val eventId = createEventBody.extractField("id")

        client.post("/api/v1/events/$eventId/activate") {
            header("X-Session-Id", sid)
        }

        client.post("/api/v1/events/$eventId/participants") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"startNumber":1,"firstName":"John","lastName":"Doe"}""")
        }

        val response = client.post("/api/v1/events/$eventId/participants") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"startNumber":1,"firstName":"Jane","lastName":"Smith"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("DUPLICATE_START_NUMBER"))
    }

    @Test
    fun `list participants returns empty list`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin"}""")
        }
        val loginBody = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }.bodyAsText()
        val sid = loginBody.extractField("sessionId")

        val createEventBody = client.post("/api/v1/events") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }.bodyAsText()
        val eventId = createEventBody.extractField("id")

        val response = client.get("/api/v1/events/$eventId/participants") {
            header("X-Session-Id", sid)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `randomize returns seed`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin"}""")
        }
        val loginBody = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }.bodyAsText()
        val sid = loginBody.extractField("sessionId")

        val createEventBody = client.post("/api/v1/events") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }.bodyAsText()
        val eventId = createEventBody.extractField("id")

        client.post("/api/v1/events/$eventId/activate") {
            header("X-Session-Id", sid)
        }

        client.post("/api/v1/events/$eventId/participants") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"startNumber":1,"firstName":"A","lastName":"A"}""")
        }
        client.post("/api/v1/events/$eventId/participants") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"startNumber":2,"firstName":"B","lastName":"B"}""")
        }

        val response = client.post("/api/v1/events/$eventId/participants/randomize") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"force":false}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"seed\""))
    }

    @Test
    fun `import csv creates participants`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin"}""")
        }
        val loginBody = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }.bodyAsText()
        val sid = loginBody.extractField("sessionId")

        val createEventBody = client.post("/api/v1/events") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }.bodyAsText()
        val eventId = createEventBody.extractField("id")

        client.post("/api/v1/events/$eventId/activate") {
            header("X-Session-Id", sid)
        }

        val response = client.post("/api/v1/events/$eventId/participants/import") {
            header("X-Session-Id", sid)
            contentType(ContentType.Application.Json)
            setBody("""{"rows":[{"startNumber":1,"firstName":"John","lastName":"Doe"},{"startNumber":2,"firstName":"Jane","lastName":"Smith"}]}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"created\":2"))
        assertTrue(body.contains("\"errors\":[]"))
    }
}
