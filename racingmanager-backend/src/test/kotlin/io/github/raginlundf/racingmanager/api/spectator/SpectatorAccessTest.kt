package io.github.raginlundf.racingmanager.api.spectator

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
import java.sql.SQLException
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Slice F.4: token-bound spectator access — issuance requires an operator
scope + tenant + eligible event status; the exchange code is single-use;
the resulting spectator token is read-only and bound to exactly one
tenant + event with no URL-level event selection surface at all. */
class SpectatorAccessTest {

    private val userRepository = UserRepository()
    private val tenantRepository = TenantRepository()
    private val membershipRepository = MembershipRepository()
    private val jwtKeyProvider = LocalJwtKeyProvider(repository = SigningKeyRepository())
    private val jwtService = JwtService(keyProvider = jwtKeyProvider)
    private val auditRepository = AuditRepository()
    private val eventRepository = EventRepository()
    private val passwordHasher = PasswordHasher()
    private val authService = AuthService(
        userRepository,
        tenantRepository,
        membershipRepository,
        RefreshTokenRepository(),
        auditRepository,
        passwordHasher,
        jwtService
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
            override fun getConnection() = throw SQLException("not used in spectator access test")
            override fun getConnection(username: String?, password: String?) = throw SQLException(
                "not used in spectator access test"
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

    private suspend fun io.ktor.client.HttpClient.adminAccessToken(): String {
        authService.setupAdmin(username = "admin", password = "password123", displayName = "Admin")
        val body = post("/api/v1/auth/login") {
            contentType(type = ContentType.Application.Json)
            setBody(body = """{"username":"admin","password":"password123"}""")
        }.bodyAsText()
        return """"accessToken":"([^"]+)"""".toRegex().find(input = body)!!.groupValues[1]
    }

    private suspend fun io.ktor.client.HttpClient.createEvent(adminToken: String, activate: Boolean = true): String {
        val createBody = post("/api/v1/events") {
            header(key = "Authorization", value = "Bearer $adminToken")
            contentType(type = ContentType.Application.Json)
            setBody(body = """{"name":"Spectator Event"}""")
        }.bodyAsText()
        val eventId = """"id":"([^"]+)"""".toRegex().find(input = createBody)!!.groupValues[1]
        if (!activate) {
            // A new event starts ACTIVE — creating another one returns this to DRAFT.
            post("/api/v1/events") {
                header(key = "Authorization", value = "Bearer $adminToken")
                contentType(type = ContentType.Application.Json)
                setBody(body = """{"name":"Takes Over"}""")
            }
        }
        return eventId
    }

    @Test
    fun `issuing a spectator token requires operator scope`() = testApplication {
        application { configureTestApp() }
        val adminToken = client.adminAccessToken()
        val eventId = client.createEvent(adminToken = adminToken)

        val response = client.post("/api/v1/events/$eventId/spectator-token")

        assertEquals(expected = HttpStatusCode.Unauthorized, actual = response.status)
    }

    @Test
    fun `issuing a spectator token for a draft event is rejected`() = testApplication {
        application { configureTestApp() }
        val adminToken = client.adminAccessToken()
        val eventId = client.createEvent(adminToken = adminToken, activate = false)

        val response = client.post("/api/v1/events/$eventId/spectator-token") {
            header(key = "Authorization", value = "Bearer $adminToken")
        }

        assertEquals(expected = HttpStatusCode.Conflict, actual = response.status)
    }

    @Test
    fun `spectator token issuance and exchange round trip yields a read-only event-bound token`() = testApplication {
        application { configureTestApp() }
        val adminToken = client.adminAccessToken()
        val eventId = client.createEvent(adminToken = adminToken)

        val tokenResponse = client.post("/api/v1/events/$eventId/spectator-token") {
            header(key = "Authorization", value = "Bearer $adminToken")
        }
        assertEquals(expected = HttpStatusCode.Created, actual = tokenResponse.status)
        val exchangeCode =
            """"exchangeCode":"([^"]+)"""".toRegex().find(input = tokenResponse.bodyAsText())!!.groupValues[1]

        val exchangeResponse = client.post("/api/v1/spectator/exchange") {
            contentType(type = ContentType.Application.Json)
            setBody(body = """{"code":"$exchangeCode"}""")
        }
        assertEquals(expected = HttpStatusCode.OK, actual = exchangeResponse.status)
        val exchangeBody = exchangeResponse.bodyAsText()
        assertTrue(actual = exchangeBody.contains(other = "\"eventId\":\"$eventId\""))
        val spectatorToken = """"accessToken":"([^"]+)"""".toRegex().find(input = exchangeBody)!!.groupValues[1]

        val principal = jwtService.verifyAccessToken(token = spectatorToken)
        assertNotNull(actual = principal)
        assertEquals(expected = setOf("rm:spectator"), actual = principal.scopes)
        assertEquals(expected = eventId, actual = principal.eventId.toString())

        val snapshotResponse = client.get("/api/v1/spectator/snapshot") {
            header(key = "Authorization", value = "Bearer $spectatorToken")
        }
        assertEquals(expected = HttpStatusCode.OK, actual = snapshotResponse.status)
        assertTrue(actual = snapshotResponse.bodyAsText().contains(other = "\"id\":\"$eventId\""))

        val createHeatAttempt = client.post("/api/v1/events/$eventId/heats") {
            header(key = "Authorization", value = "Bearer $spectatorToken")
            contentType(type = ContentType.Application.Json)
            setBody(body = """{"participantIds":[]}""")
        }
        assertEquals(expected = HttpStatusCode.Forbidden, actual = createHeatAttempt.status)
    }

    @Test
    fun `an exchange code can only be used once`() = testApplication {
        application { configureTestApp() }
        val adminToken = client.adminAccessToken()
        val eventId = client.createEvent(adminToken = adminToken)

        val tokenResponse = client.post("/api/v1/events/$eventId/spectator-token") {
            header(key = "Authorization", value = "Bearer $adminToken")
        }
        val exchangeCode = """"exchangeCode":"([^"]+)"""".toRegex()
            .find(input = tokenResponse.bodyAsText())!!.groupValues[1]

        val first = client.post("/api/v1/spectator/exchange") {
            contentType(type = ContentType.Application.Json)
            setBody(body = """{"code":"$exchangeCode"}""")
        }
        assertEquals(expected = HttpStatusCode.OK, actual = first.status)

        val second = client.post("/api/v1/spectator/exchange") {
            contentType(type = ContentType.Application.Json)
            setBody(body = """{"code":"$exchangeCode"}""")
        }
        assertEquals(expected = HttpStatusCode.BadRequest, actual = second.status)
    }

    @Test
    fun `a spectator token from one event cannot read a different event via a token from another event`() =
        testApplication {
            application { configureTestApp() }
            val adminToken = client.adminAccessToken()
            val eventAId = client.createEvent(adminToken)
            val eventBId = client.createEvent(adminToken)
            // Creating B made it the active event; A must be running again to be watchable.
            client.post("/api/v1/events/$eventAId/activate") {
                header(key = "Authorization", value = "Bearer $adminToken")
            }

            val tokenResponseA = client.post("/api/v1/events/$eventAId/spectator-token") {
                header(key = "Authorization", value = "Bearer $adminToken")
            }
            val codeA =
                """"exchangeCode":"([^"]+)"""".toRegex().find(input = tokenResponseA.bodyAsText())!!.groupValues[1]
            val exchangeA = client.post("/api/v1/spectator/exchange") {
                contentType(type = ContentType.Application.Json)
                setBody(body = """{"code":"$codeA"}""")
            }.bodyAsText()
            val spectatorTokenA = """"accessToken":"([^"]+)"""".toRegex().find(input = exchangeA)!!.groupValues[1]

            val principal = jwtService.verifyAccessToken(token = spectatorTokenA)!!
            assertEquals(expected = eventAId, actual = principal.eventId.toString())
            assertTrue(actual = eventAId != eventBId)

            val snapshotResponse = client.get("/api/v1/spectator/snapshot") {
                header("Authorization", "Bearer $spectatorTokenA")
            }
            assertTrue(actual = snapshotResponse.bodyAsText().contains(other = "\"id\":\"$eventAId\""))
            assertTrue(actual = !snapshotResponse.bodyAsText().contains(other = "\"id\":\"$eventBId\""))
        }

    @Test
    fun `the spectator websocket enforces the token's event binding and rejects a missing token`() = testApplication {
        application {
            configureTestApp()
        }
        val wsClient = createClient { install(plugin = WebSockets) }
        val adminToken = client.adminAccessToken()
        val eventId = client.createEvent(adminToken)

        val tokenResponse = client.post("/api/v1/events/$eventId/spectator-token") {
            header("Authorization", "Bearer $adminToken")
        }
        val exchangeCode = """"exchangeCode":"([^"]+)"""".toRegex()
            .find(input = tokenResponse.bodyAsText())!!.groupValues[1]
        val exchangeBody = client.post("/api/v1/spectator/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"$exchangeCode"}""")
        }.bodyAsText()
        val spectatorToken = """"accessToken":"([^"]+)"""".toRegex().find(input = exchangeBody)!!.groupValues[1]

        wsClient.webSocket("/api/v1/spectator/live") {
            val reason = withTimeoutOrNull(timeout = 6_000.milliseconds) { closeReason.await() }
            assertNotNull(actual = reason, message = "connection without an auth frame must be closed")
        }

        wsClient.webSocket("/api/v1/spectator/live") {
            send(Frame.Text("""{"type":"auth","token":"$adminToken"}"""))
            val reason = withTimeoutOrNull(timeout = 2_000.milliseconds) { closeReason.await() }
            assertNotNull(
                actual = reason,
                message = "an operator (rm:admin) token has no rm:spectator scope and must be rejected"
            )
        }

        wsClient.webSocket("/api/v1/spectator/live") {
            send(Frame.Text("""{"type":"auth","token":"$spectatorToken"}"""))
            val reason = withTimeoutOrNull(timeout = 500.milliseconds) { closeReason.await() }
            assertEquals(
                expected = null,
                actual = reason,
                message = "a valid spectator token must keep the connection open"
            )
            close()
        }
    }
}
