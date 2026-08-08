package io.github.raginlundf.racingmanager.api.hardware

import io.github.raginlundf.racingmanager.api.testRaceDeviceGateway
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.configureWebSockets
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceMode
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceSettings
import io.github.raginlundf.racingmanager.infrastructure.gateway.ReconfigurableMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.buildRaceDeviceGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.Esp32WebSocketDirectMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.Esp32WebSocketDirectSettings
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.protocol.Esp32Message
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.protocol.Esp32MessageCodec
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Drives the ESP32 hardware WebSocket endpoint over the real socket boundary —
    there is no JWT here, so the gating under test is deployment mode + active
    race-device mode, and the gateway's own device-id allowlist. */
class HardwareRoutesTest {

    @Test
    fun `rejects a connection outside a local deployment`() = testApplication {
        application { configureTestApp(deploymentMode = DeploymentMode.HOSTED) }
        val wsClient = createClient { install(plugin = WebSockets) }

        wsClient.webSocket("/hardware/esp32/ws") {
            val reason = withTimeoutOrNull(timeout = 2_000.milliseconds) { closeReason.await() }
            assertNotNull(actual = reason, message = "a hosted deployment must not accept ESP32 connections")
        }
    }

    @Test
    fun `rejects a connection when the race device is not in ESP32_WEBSOCKET_DIRECT mode`() = testApplication {
        // testRaceDeviceGateway() is always the in-process simulator, regardless of settings.
        application { configureTestApp(gateway = testRaceDeviceGateway()) }
        val wsClient = createClient { install(plugin = WebSockets) }

        wsClient.webSocket("/hardware/esp32/ws") {
            val reason = withTimeoutOrNull(timeout = 2_000.milliseconds) { closeReason.await() }
            assertNotNull(actual = reason, message = "a non-ESP32 active mode must not accept ESP32 connections")
        }
    }

    @Test
    fun `accepts a known device, registers it, and reports it as connected`() = testApplication {
        val deviceId = "lane-1-start"
        val gateway = ReconfigurableMeasurementGateway(
            initialSettings = RaceDeviceSettings(
                mode = RaceDeviceMode.ESP32_WEBSOCKET_DIRECT,
                endpoint = "ws://unused",
                finishTimeoutMs = 30_000,
                esp32 = Esp32WebSocketDirectSettings(expectedDeviceIds = listOf(deviceId)),
            ),
            buildDelegate = ::buildRaceDeviceGateway,
        )
        application { configureTestApp(gateway = gateway) }
        val wsClient = createClient { install(plugin = WebSockets) }

        wsClient.webSocket("/hardware/esp32/ws") {
            send(
                Frame.Text(
                    text = Esp32MessageCodec.encode(
                        message = Esp32Message.DeviceRegister(
                            deviceId = deviceId,
                            bootId = "boot-1",
                            role = "start",
                            firmware = "0.1.0",
                        ),
                    ),
                ),
            )
            withTimeoutOrNull(timeout = 2_000.milliseconds) {
                while (gateway.esp32Gateway().deviceSnapshots().none { it.deviceId == deviceId && it.connected }) {
                    yield()
                }
            }
            assertTrue(actual = gateway.esp32Gateway().deviceSnapshots().first { it.deviceId == deviceId }.connected)
            close()
        }
    }

    private fun ReconfigurableMeasurementGateway.esp32Gateway(): Esp32WebSocketDirectMeasurementGateway {
        return currentDelegate() as Esp32WebSocketDirectMeasurementGateway
    }

    private fun Application.configureTestApp(
        gateway: ReconfigurableMeasurementGateway = testRaceDeviceGateway(),
        deploymentMode: DeploymentMode = DeploymentMode.LOCAL,
    ) {
        configureWebSockets()
        routing {
            hardwareRoutes(gateway = gateway, deploymentMode = deploymentMode)
        }
    }
}
