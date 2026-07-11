package io.github.raginlundf.racingmanager.api.auth

import io.github.raginlundf.racingmanager.api.configureRouting
import io.github.raginlundf.racingmanager.api.configureSerialization
import io.github.raginlundf.racingmanager.api.configureStatusPages
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SessionRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
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
    private val sessionRepository = SessionRepository()
    private val auditRepository = AuditRepository()
    private val passwordHasher = PasswordHasher()
    private val authService = AuthService(userRepository, sessionRepository, auditRepository, passwordHasher)

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
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
        assertTrue(response.bodyAsText().contains("\"firstRun\":true"))
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
    fun `login with valid credentials returns 200 and session`() = testApplication {
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
        assertTrue(body.contains("\"sessionId\""))
        assertTrue(body.contains("\"username\":\"admin\""))
        assertTrue(body.contains("\"role\":\"ADMIN\""))
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
    fun `session returns user info for valid session`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }

        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sessionId = extractSessionId(loginResponse.bodyAsText())

        val response = client.get("/api/v1/auth/session") {
            header("X-Session-Id", sessionId)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"username\":\"admin\""))
        assertTrue(body.contains("\"role\":\"ADMIN\""))
    }

    @Test
    fun `session returns 401 for missing header`() = testApplication {
        application { configureTestApp() }

        val response = client.get("/api/v1/auth/session")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"MISSING_SESSION\""))
    }

    @Test
    fun `session returns 401 for unknown session`() = testApplication {
        application { configureTestApp() }

        val response = client.get("/api/v1/auth/session") {
            header("X-Session-Id", "00000000-0000-0000-0000-000000000000")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"SESSION_NOT_FOUND\""))
    }

    @Test
    fun `logout returns 204 and invalidates session`() = testApplication {
        application { configureTestApp() }

        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123","displayName":"Admin User"}""")
        }

        val loginResponse = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }
        val sessionId = extractSessionId(loginResponse.bodyAsText())

        val logoutResponse = client.post("/api/v1/auth/logout") {
            header("X-Session-Id", sessionId)
        }

        assertEquals(HttpStatusCode.NoContent, logoutResponse.status)

        val sessionResponse = client.get("/api/v1/auth/session") {
            header("X-Session-Id", sessionId)
        }
        assertEquals(HttpStatusCode.Unauthorized, sessionResponse.status)
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

    private fun Application.configureTestApp() {
        configureSerialization()
        configureStatusPages()
        configureRouting(authService)
    }

    private fun extractSessionId(body: String): String {
        val regex = """"sessionId":"([^"]+)"""".toRegex()
        return regex.find(body)?.groupValues?.get(1)
            ?: throw IllegalStateException("Could not extract sessionId from: $body")
    }
}
