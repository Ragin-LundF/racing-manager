package io.github.raginlundf.racingmanager.api.racedevice

import io.github.raginlundf.racingmanager.api.configureSerialization
import io.github.raginlundf.racingmanager.api.configureStatusPages
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceMode
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceSettings
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaspberryPiMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.ReconfigurableMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.ArduinoTwoLaneSettings
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.FinishSemantics
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.Esp32WebSocketDirectSettings
import io.github.raginlundf.racingmanager.infrastructure.repositories.RaceDeviceSettingsRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.security.LocalJwtKeyProvider
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
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Drives the race-device routes over the real HTTP boundary. Only these routes are
    mounted — the same call `configureRouting` makes — so the test does not have to
    build every unrelated service. */
class RaceDeviceRoutesTest {

    private val jwtKeyProvider = LocalJwtKeyProvider(repository = SigningKeyRepository())
    private val jwtService = JwtService(keyProvider = jwtKeyProvider)
    private val settingsRepository = RaceDeviceSettingsRepository()

    private val arduinoBody = """
        {
          "portName": "/dev/tty.usbmodem1101",
          "baudRate": 115200,
          "readyTimeoutMs": 10000,
          "falseStartWindowMs": 250,
          "finishSemantics": "TIMESTAMP",
          "rawLogPath": "raw-timing.log"
        }
    """.trimIndent()

    private val esp32Body = """
        {
          "expectedDeviceIds": ["lane-1-start", "lane-1-finish", "lane-2-start", "lane-2-finish"],
          "registerTimeoutMs": 10000,
          "useRaceControlHandshake": false,
          "useTimeSync": false,
          "useDeviceHeartbeat": true,
          "heartbeatTimeoutMs": 5000,
          "armTimeoutMs": 5000,
          "timeSyncRounds": 5,
          "rawLogPath": "raw-esp32-timing.log"
        }
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        jwtKeyProvider.ensureKeyExists()
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    @Test
    fun `returns the active settings including the arduino block`() = testApplication {
        application {
            configureTestApp(
                settings = RaceDeviceSettings(
                    mode = RaceDeviceMode.ARDUINO_TWO_LANE,
                    endpoint = "ws://unused",
                    finishTimeoutMs = 30_000,
                    arduino = ArduinoTwoLaneSettings(portName = "/dev/ttyACM0"),
                ),
            )
        }

        val response = client.get("/api/v1/racedevice/settings") { adminAuth() }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        val body = response.bodyAsText()
        assertContains(charSequence = body, other = "\"mode\":\"ARDUINO_TWO_LANE\"")
        assertContains(charSequence = body, other = "\"portName\":\"/dev/ttyACM0\"")
        assertContains(charSequence = body, other = "\"baudRate\":115200")
        assertContains(charSequence = body, other = "\"finishSemantics\":\"ELAPSED\"")
    }

    @Test
    fun `saves the arduino mode and persists the serial options`() = testApplication {
        application { configureTestApp() }

        val response = client.put("/api/v1/racedevice/settings") {
            adminAuth()
            contentType(ContentType.Application.Json)
            setBody(
                """{"mode":"ARDUINO_TWO_LANE","endpoint":"ws://unused","finishTimeoutMs":45000,
                   "arduino":$arduinoBody}""",
            )
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        val saved = settingsRepository.find()
        assertNotNull(actual = saved)
        assertEquals(
            expected = RaceDeviceSettings(
                mode = RaceDeviceMode.ARDUINO_TWO_LANE,
                endpoint = "ws://unused",
                finishTimeoutMs = 45_000,
                arduino = ArduinoTwoLaneSettings(
                    portName = "/dev/tty.usbmodem1101",
                    baudRate = 115_200,
                    readyTimeoutMs = 10_000,
                    falseStartWindowMs = 250,
                    finishSemantics = FinishSemantics.TIMESTAMP,
                    rawLogPath = "raw-timing.log",
                ),
            ),
            actual = saved,
        )
    }

    @Test
    fun `rejects the arduino mode without serial options`() = testApplication {
        application { configureTestApp() }

        val response = client.put("/api/v1/racedevice/settings") {
            adminAuth()
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"ARDUINO_TWO_LANE","endpoint":"ws://unused","finishTimeoutMs":30000}""")
        }

        assertEquals(expected = HttpStatusCode.BadRequest, actual = response.status)
    }

    @Test
    fun `rejects unusable serial options`() = testApplication {
        application { configureTestApp() }

        val invalidBlocks = listOf(
            arduinoBody.replace(oldValue = "/dev/tty.usbmodem1101", newValue = "  "),
            arduinoBody.replace(oldValue = "\"baudRate\": 115200", newValue = "\"baudRate\": 0"),
            arduinoBody.replace(oldValue = "\"readyTimeoutMs\": 10000", newValue = "\"readyTimeoutMs\": 0"),
            arduinoBody.replace(oldValue = "\"falseStartWindowMs\": 250", newValue = "\"falseStartWindowMs\": -1"),
            arduinoBody.replace(oldValue = "\"TIMESTAMP\"", newValue = "\"SOMETIMES\""),
            arduinoBody.replace(oldValue = "\"raw-timing.log\"", newValue = "\"\""),
        )

        invalidBlocks.forEach { block ->
            val response = client.put("/api/v1/racedevice/settings") {
                adminAuth()
                contentType(ContentType.Application.Json)
                setBody("""{"mode":"ARDUINO_TWO_LANE","endpoint":"ws://unused","finishTimeoutMs":30000,
                          "arduino":$block}""")
            }
            assertEquals(expected = HttpStatusCode.BadRequest, actual = response.status, message = block)
        }
    }

    @Test
    fun `rejects a non-positive finish timeout`() = testApplication {
        application { configureTestApp() }

        val response = client.put("/api/v1/racedevice/settings") {
            adminAuth()
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"ARDUINO_TWO_LANE","endpoint":"ws://unused","finishTimeoutMs":0,
                      "arduino":$arduinoBody}""")
        }

        assertEquals(expected = HttpStatusCode.BadRequest, actual = response.status)
    }

    @Test
    fun `reports a failure when the configured serial port cannot be opened`() = testApplication {
        application { configureTestApp() }

        val response = client.post("/api/v1/racedevice/test") {
            adminAuth()
            contentType(ContentType.Application.Json)
            setBody(
                """{"mode":"ARDUINO_TWO_LANE","endpoint":"ws://unused","arduino":
                   ${arduinoBody.replace(oldValue = "/dev/tty.usbmodem1101", newValue = "/dev/no-such-port")
                    .replace(oldValue = "\"readyTimeoutMs\": 10000", newValue = "\"readyTimeoutMs\": 200")}}""",
            )
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        val body = response.bodyAsText()
        assertContains(charSequence = body, other = "\"ok\":false")
        assertContains(charSequence = body, other = "\"error\"")
    }

    @Test
    fun `rejects a connection test for the arduino mode without serial options`() = testApplication {
        application { configureTestApp() }

        val response = client.post("/api/v1/racedevice/test") {
            adminAuth()
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"ARDUINO_TWO_LANE","endpoint":"ws://unused"}""")
        }

        assertEquals(expected = HttpStatusCode.BadRequest, actual = response.status)
    }

    @Test
    fun `returns the active settings including the esp32 block`() = testApplication {
        application {
            configureTestApp(
                settings = RaceDeviceSettings(
                    mode = RaceDeviceMode.ESP32_WEBSOCKET_DIRECT,
                    endpoint = "ws://unused",
                    finishTimeoutMs = 30_000,
                    esp32 = Esp32WebSocketDirectSettings(expectedDeviceIds = listOf("lane-1-start")),
                ),
            )
        }

        val response = client.get("/api/v1/racedevice/settings") { adminAuth() }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        val body = response.bodyAsText()
        assertContains(charSequence = body, other = "\"mode\":\"ESP32_WEBSOCKET_DIRECT\"")
        assertContains(charSequence = body, other = "\"expectedDeviceIds\":[\"lane-1-start\"]")
    }

    @Test
    fun `saves the esp32 mode and persists the options`() = testApplication {
        application { configureTestApp() }

        val response = client.put("/api/v1/racedevice/settings") {
            adminAuth()
            contentType(ContentType.Application.Json)
            setBody(
                """{"mode":"ESP32_WEBSOCKET_DIRECT","endpoint":"ws://unused","finishTimeoutMs":45000,
                   "esp32":$esp32Body}""",
            )
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        val saved = settingsRepository.find()
        assertNotNull(actual = saved)
        assertEquals(
            expected = RaceDeviceSettings(
                mode = RaceDeviceMode.ESP32_WEBSOCKET_DIRECT,
                endpoint = "ws://unused",
                finishTimeoutMs = 45_000,
                esp32 = Esp32WebSocketDirectSettings(
                    expectedDeviceIds = listOf("lane-1-start", "lane-1-finish", "lane-2-start", "lane-2-finish"),
                    registerTimeoutMs = 10_000,
                    rawLogPath = "raw-esp32-timing.log",
                ),
            ),
            actual = saved,
        )
    }

    @Test
    fun `rejects the esp32 mode without device options`() = testApplication {
        application { configureTestApp() }

        val response = client.put("/api/v1/racedevice/settings") {
            adminAuth()
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"ESP32_WEBSOCKET_DIRECT","endpoint":"ws://unused","finishTimeoutMs":30000}""")
        }

        assertEquals(expected = HttpStatusCode.BadRequest, actual = response.status)
    }

    @Test
    fun `rejects unusable esp32 options`() = testApplication {
        application { configureTestApp() }

        val emptyDeviceIds = "\"lane-1-start\", \"lane-1-finish\", \"lane-2-start\", \"lane-2-finish\""
        val invalidBlocks = listOf(
            esp32Body.replace(oldValue = emptyDeviceIds, newValue = ""),
            esp32Body.replace(oldValue = "\"registerTimeoutMs\": 10000", newValue = "\"registerTimeoutMs\": 0"),
            esp32Body.replace(oldValue = "\"heartbeatTimeoutMs\": 5000", newValue = "\"heartbeatTimeoutMs\": 0"),
            esp32Body.replace(oldValue = "\"armTimeoutMs\": 5000", newValue = "\"armTimeoutMs\": 0"),
            esp32Body.replace(oldValue = "\"timeSyncRounds\": 5", newValue = "\"timeSyncRounds\": 0"),
            esp32Body.replace(oldValue = "\"raw-esp32-timing.log\"", newValue = "\"\""),
            // Not implemented yet — see Esp32WebSocketDirectMeasurementGateway.
            esp32Body.replace(
                oldValue = "\"useRaceControlHandshake\": false",
                newValue = "\"useRaceControlHandshake\": true",
            ),
            esp32Body.replace(oldValue = "\"useTimeSync\": false", newValue = "\"useTimeSync\": true"),
        )

        invalidBlocks.forEach { block ->
            val response = client.put("/api/v1/racedevice/settings") {
                adminAuth()
                contentType(ContentType.Application.Json)
                setBody("""{"mode":"ESP32_WEBSOCKET_DIRECT","endpoint":"ws://unused","finishTimeoutMs":30000,
                          "esp32":$block}""")
            }
            assertEquals(expected = HttpStatusCode.BadRequest, actual = response.status, message = block)
        }
    }

    @Test
    fun `reports which expected esp32 devices are connected`() = testApplication {
        application { configureTestApp() }

        val response = client.post("/api/v1/racedevice/test") {
            adminAuth()
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"ESP32_WEBSOCKET_DIRECT","endpoint":"ws://unused","esp32":$esp32Body}""")
        }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertContains(charSequence = response.bodyAsText(), other = "\"ok\":false")
    }

    @Test
    fun `lists no esp32 devices when the gateway is not in that mode`() = testApplication {
        application { configureTestApp() }

        val response = client.get("/api/v1/racedevice/esp32/devices") { adminAuth() }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertEquals(expected = "[]", actual = response.bodyAsText())
    }

    @Test
    fun `denies the esp32 device list without the admin scope`() = testApplication {
        application { configureTestApp() }

        val response = client.get("/api/v1/racedevice/esp32/devices") {
            header("Authorization", "Bearer ${token(scope = Scopes.USER)}")
        }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
    }

    @Test
    fun `hides the esp32 device list outside a local deployment`() = testApplication {
        application { configureTestApp(deploymentMode = DeploymentMode.HOSTED) }

        val response = client.get("/api/v1/racedevice/esp32/devices") { adminAuth() }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
    }

    @Test
    fun `lists the serial ports of the host for an admin`() = testApplication {
        application { configureTestApp() }

        val response = client.get("/api/v1/racedevice/serialports") { adminAuth() }

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertTrue(actual = response.bodyAsText().startsWith(prefix = "["))
    }

    @Test
    fun `denies the serial port listing without the admin scope`() = testApplication {
        application { configureTestApp() }

        val response = client.get("/api/v1/racedevice/serialports") {
            header("Authorization", "Bearer ${token(scope = Scopes.USER)}")
        }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
    }

    @Test
    fun `hides the serial port listing outside a local deployment`() = testApplication {
        application { configureTestApp(deploymentMode = DeploymentMode.HOSTED) }

        val response = client.get("/api/v1/racedevice/serialports") { adminAuth() }

        assertEquals(expected = HttpStatusCode.Forbidden, actual = response.status)
        assertContains(charSequence = response.bodyAsText(), other = "NOT_LOCAL")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.adminAuth() {
        header("Authorization", "Bearer ${token(scope = Scopes.ADMIN)}")
    }

    private fun token(scope: String): String {
        return jwtService.issueAccessToken(
            userId = UUID.randomUUID(),
            tenantId = UUID.randomUUID(),
            scopes = setOf(scope),
            ttl = 5.minutes,
        )
    }

    private fun Application.configureTestApp(
        settings: RaceDeviceSettings = RaceDeviceSettings(
            mode = RaceDeviceMode.SIMULATED,
            endpoint = "ws://test",
            finishTimeoutMs = 30_000,
        ),
        deploymentMode: DeploymentMode = DeploymentMode.LOCAL,
    ) {
        configureSerialization()
        configureStatusPages()
        routing {
            raceDeviceRoutes(
                jwtService = jwtService,
                // The delegate is always the in-process simulator: saving settings must
                // not open a real port from a test.
                gateway = ReconfigurableMeasurementGateway(
                    initialSettings = settings,
                    buildDelegate = { RaspberryPiMeasurementGateway.simulated() },
                ),
                settingsRepository = settingsRepository,
                deploymentMode = deploymentMode,
            )
        }
    }
}
