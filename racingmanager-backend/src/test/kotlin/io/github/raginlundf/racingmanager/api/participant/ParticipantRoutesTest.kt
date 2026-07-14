package io.github.raginlundf.racingmanager.api.participant

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

class ParticipantRoutesTest {

    private val userRepository = UserRepository()
    private val jwtKeyProvider = LocalJwtKeyProvider(repository = SigningKeyRepository())
    private val jwtService = JwtService(jwtKeyProvider)
    private val auditRepository = AuditRepository()
    private val eventRepository = EventRepository()
    private val participantRepository = ParticipantRepository()
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
    private val auditService = AuditService(auditRepository)
    private val diagnosticsService = DiagnosticsService(
        object : DataSource {
            override fun getConnection() = throw SQLException("not used in participant test")
            override fun getConnection(username: String?, password: String?) = throw SQLException(
                "not used in participant test"
            )

            override fun getLogWriter() = null

            @Suppress("EmptyFunctionBlock")
            override fun setLogWriter(out: PrintWriter?) {
            }

            @Suppress("EmptyFunctionBlock")
            override fun setLoginTimeout(seconds: Int) {
            }

            override fun getLoginTimeout() = 0
            override fun <T> unwrap(iface: Class<T>?) = throw SQLException("not a wrapper")
            override fun isWrapperFor(iface: Class<*>?) = false
            override fun getParentLogger() = Logger.getLogger("")
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

    @Throws(IllegalStateException::class)
    private fun String.extractField(field: String): String {
        val regex = """"$field":"([^"]+)"""".toRegex()
        return regex.find(input = this)?.groupValues?.get(1)
            ?: error("Could not extract $field from: $this")
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
        val sid = loginBody.extractField(field = "accessToken")

        val createEventBody = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }.bodyAsText()
        val eventId = createEventBody.extractField(field = "id")

        client.post("/api/v1/events/$eventId/activate") {
            header("Authorization", "Bearer $sid")
        }

        val response = client.post("/api/v1/events/$eventId/participants") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"startNumber":1,"firstName":"John","lastName":"Doe"}""")
        }

        assertEquals(expected = HttpStatusCode.Created, actual = response.status)
        val body = response.bodyAsText()
        assertTrue(actual = body.contains("\"startNumber\":1"))
        assertTrue(actual = body.contains("\"firstName\":\"John\""))
        assertTrue(actual = body.contains("\"status\":\"ACTIVE\""))
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
        val sid = loginBody.extractField("accessToken")

        val createEventBody = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }.bodyAsText()
        val eventId = createEventBody.extractField(field = "id")

        client.post("/api/v1/events/$eventId/activate") {
            header("Authorization", "Bearer $sid")
        }

        client.post("/api/v1/events/$eventId/participants") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"startNumber":1,"firstName":"John","lastName":"Doe"}""")
        }

        val response = client.post("/api/v1/events/$eventId/participants") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"startNumber":1,"firstName":"Jane","lastName":"Smith"}""")
        }

        assertEquals(expected = HttpStatusCode.Conflict, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("DUPLICATE_START_NUMBER"))
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
        val sid = loginBody.extractField(field = "accessToken")

        val createEventBody = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }.bodyAsText()
        val eventId = createEventBody.extractField(field = "id")

        val response = client.get("/api/v1/events/$eventId/participants") {
            header("Authorization", "Bearer $sid")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertEquals(expected = "[]", actual = response.bodyAsText())
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
        val sid = loginBody.extractField(field = "accessToken")

        val createEventBody = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }.bodyAsText()
        val eventId = createEventBody.extractField(field = "id")

        client.post("/api/v1/events/$eventId/activate") {
            header("Authorization", "Bearer $sid")
        }

        client.post("/api/v1/events/$eventId/participants") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"startNumber":1,"firstName":"A","lastName":"A"}""")
        }
        client.post("/api/v1/events/$eventId/participants") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"startNumber":2,"firstName":"B","lastName":"B"}""")
        }

        val response = client.post("/api/v1/events/$eventId/participants/randomize") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"force":false}""")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("\"seed\""))
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
        val sid = loginBody.extractField(field = "accessToken")

        val createEventBody = client.post("/api/v1/events") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test Event"}""")
        }.bodyAsText()
        val eventId = createEventBody.extractField(field = "id")

        client.post("/api/v1/events/$eventId/activate") {
            header("Authorization", "Bearer $sid")
        }

        val response = client.post("/api/v1/events/$eventId/participants/import") {
            header("Authorization", "Bearer $sid")
            contentType(ContentType.Application.Json)
            setBody(
                """{"rows":[
                |{"startNumber":1,"firstName":"John","lastName":"Doe"},
                |{"startNumber":2,"firstName":"Jane","lastName":"Smith"}
                |]}""".trimMargin()
            )
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        val body = response.bodyAsText()
        assertTrue(actual = body.contains("\"created\":2"))
        assertTrue(actual = body.contains("\"errors\":[]"))
    }
}
