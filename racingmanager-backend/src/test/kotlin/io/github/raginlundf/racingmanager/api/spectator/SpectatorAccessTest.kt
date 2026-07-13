package io.github.raginlundf.racingmanager.api.spectator

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
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
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
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Slice F.4: token-bound spectator access — issuance requires an operator
    scope + tenant + eligible event status; the exchange code is single-use;
    the resulting spectator token is read-only and bound to exactly one
    tenant + event with no URL-level event selection surface at all. */
class SpectatorAccessTest {

    private val userRepository = UserRepository()
    private val tenantRepository = TenantRepository()
    private val membershipRepository = MembershipRepository()
    private val jwtKeyProvider = LocalJwtKeyProvider(SigningKeyRepository())
    private val jwtService = JwtService(jwtKeyProvider)
    private val auditRepository = AuditRepository()
    private val eventRepository = EventRepository()
    private val passwordHasher = PasswordHasher()
    private val authService = AuthService(userRepository, tenantRepository, membershipRepository, RefreshTokenRepository(), auditRepository, passwordHasher, jwtService)
    private val participantRepository = ParticipantRepository()
    private val eventService = EventService(eventRepository, participantRepository, auditRepository)
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
            override fun getConnection() = throw java.sql.SQLException("not used in spectator access test")
            override fun getConnection(username: String?, password: String?) = throw java.sql.SQLException("not used in spectator access test")
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

    private suspend fun io.ktor.client.HttpClient.adminAccessToken(): String {
        authService.setupAdmin("admin", "password123", "Admin")
        val body = post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"password123"}""")
        }.bodyAsText()
        return """"accessToken":"([^"]+)"""".toRegex().find(body)!!.groupValues[1]
    }

    private suspend fun io.ktor.client.HttpClient.createEvent(adminToken: String, activate: Boolean = true): String {
        val createBody = post("/api/v1/events") {
            header("Authorization", "Bearer $adminToken")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Spectator Event"}""")
        }.bodyAsText()
        val eventId = """"id":"([^"]+)"""".toRegex().find(createBody)!!.groupValues[1]
        if (activate) {
            post("/api/v1/events/$eventId/activate") {
                header("Authorization", "Bearer $adminToken")
            }
        }
        return eventId
    }

    @Test
    fun `issuing a spectator token requires operator scope`() = testApplication {
        application { configureTestApp() }
        val adminToken = client.adminAccessToken()
        val eventId = client.createEvent(adminToken)

        val response = client.post("/api/v1/events/$eventId/spectator-token")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `issuing a spectator token for a draft event is rejected`() = testApplication {
        application { configureTestApp() }
        val adminToken = client.adminAccessToken()
        val eventId = client.createEvent(adminToken, activate = false)

        val response = client.post("/api/v1/events/$eventId/spectator-token") {
            header("Authorization", "Bearer $adminToken")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `spectator token issuance and exchange round trip yields a read-only event-bound token`() = testApplication {
        application { configureTestApp() }
        val adminToken = client.adminAccessToken()
        val eventId = client.createEvent(adminToken)

        val tokenResponse = client.post("/api/v1/events/$eventId/spectator-token") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.Created, tokenResponse.status)
        val exchangeCode = """"exchangeCode":"([^"]+)"""".toRegex().find(tokenResponse.bodyAsText())!!.groupValues[1]

        val exchangeResponse = client.post("/api/v1/spectator/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"$exchangeCode"}""")
        }
        assertEquals(HttpStatusCode.OK, exchangeResponse.status)
        val exchangeBody = exchangeResponse.bodyAsText()
        assertTrue(exchangeBody.contains("\"eventId\":\"$eventId\""))
        val spectatorToken = """"accessToken":"([^"]+)"""".toRegex().find(exchangeBody)!!.groupValues[1]

        val principal = jwtService.verifyAccessToken(spectatorToken)
        assertNotNull(principal)
        assertEquals(setOf("rm:spectator"), principal.scopes)
        assertEquals(eventId, principal.eventId.toString())

        val snapshotResponse = client.get("/api/v1/spectator/snapshot") {
            header("Authorization", "Bearer $spectatorToken")
        }
        assertEquals(HttpStatusCode.OK, snapshotResponse.status)
        assertTrue(snapshotResponse.bodyAsText().contains("\"id\":\"$eventId\""))

        val createHeatAttempt = client.post("/api/v1/events/$eventId/heats") {
            header("Authorization", "Bearer $spectatorToken")
            contentType(ContentType.Application.Json)
            setBody("""{"participantIds":[]}""")
        }
        assertEquals(HttpStatusCode.Forbidden, createHeatAttempt.status)
    }

    @Test
    fun `an exchange code can only be used once`() = testApplication {
        application { configureTestApp() }
        val adminToken = client.adminAccessToken()
        val eventId = client.createEvent(adminToken)

        val tokenResponse = client.post("/api/v1/events/$eventId/spectator-token") {
            header("Authorization", "Bearer $adminToken")
        }
        val exchangeCode = """"exchangeCode":"([^"]+)"""".toRegex().find(tokenResponse.bodyAsText())!!.groupValues[1]

        val first = client.post("/api/v1/spectator/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"$exchangeCode"}""")
        }
        assertEquals(HttpStatusCode.OK, first.status)

        val second = client.post("/api/v1/spectator/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"$exchangeCode"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, second.status)
    }

    @Test
    fun `a spectator token from one event cannot read a different event via a token from another event`() = testApplication {
        application { configureTestApp() }
        val adminToken = client.adminAccessToken()
        val eventAId = client.createEvent(adminToken)
        val eventBId = client.createEvent(adminToken)

        val tokenResponseA = client.post("/api/v1/events/$eventAId/spectator-token") {
            header("Authorization", "Bearer $adminToken")
        }
        val codeA = """"exchangeCode":"([^"]+)"""".toRegex().find(tokenResponseA.bodyAsText())!!.groupValues[1]
        val exchangeA = client.post("/api/v1/spectator/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"$codeA"}""")
        }.bodyAsText()
        val spectatorTokenA = """"accessToken":"([^"]+)"""".toRegex().find(exchangeA)!!.groupValues[1]

        val principal = jwtService.verifyAccessToken(spectatorTokenA)!!
        assertEquals(eventAId, principal.eventId.toString())
        assertTrue(eventAId != eventBId)

        val snapshotResponse = client.get("/api/v1/spectator/snapshot") {
            header("Authorization", "Bearer $spectatorTokenA")
        }
        assertTrue(snapshotResponse.bodyAsText().contains("\"id\":\"$eventAId\""))
        assertTrue(!snapshotResponse.bodyAsText().contains("\"id\":\"$eventBId\""))
    }

    @Test
    fun `the spectator websocket enforces the token's event binding and rejects a missing token`() = testApplication {
        application {
            configureTestApp()
        }
        val wsClient = createClient { install(WebSockets) }
        val adminToken = client.adminAccessToken()
        val eventId = client.createEvent(adminToken)

        val tokenResponse = client.post("/api/v1/events/$eventId/spectator-token") {
            header("Authorization", "Bearer $adminToken")
        }
        val exchangeCode = """"exchangeCode":"([^"]+)"""".toRegex().find(tokenResponse.bodyAsText())!!.groupValues[1]
        val exchangeBody = client.post("/api/v1/spectator/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"$exchangeCode"}""")
        }.bodyAsText()
        val spectatorToken = """"accessToken":"([^"]+)"""".toRegex().find(exchangeBody)!!.groupValues[1]

        wsClient.webSocket("/api/v1/spectator/live") {
            val reason = withTimeoutOrNull(6_000) { closeReason.await() }
            assertNotNull(reason, "connection without an auth frame must be closed")
        }

        wsClient.webSocket("/api/v1/spectator/live") {
            send(Frame.Text("""{"type":"auth","token":"$adminToken"}"""))
            val reason = withTimeoutOrNull(2_000) { closeReason.await() }
            assertNotNull(reason, "an operator (rm:admin) token has no rm:spectator scope and must be rejected")
        }

        wsClient.webSocket("/api/v1/spectator/live") {
            send(Frame.Text("""{"type":"auth","token":"$spectatorToken"}"""))
            val reason = withTimeoutOrNull(500) { closeReason.await() }
            assertEquals(null, reason, "a valid spectator token must keep the connection open")
            close()
        }
    }
}
