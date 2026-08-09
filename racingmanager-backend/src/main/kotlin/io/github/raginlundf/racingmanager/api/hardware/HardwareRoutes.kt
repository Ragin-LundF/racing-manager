package io.github.raginlundf.racingmanager.api.hardware

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.gateway.ReconfigurableMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.Esp32WebSocketDirectMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.KtorEsp32DeviceSession
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close

private val logger = KotlinLogging.logger {}

/** Inbound WebSocket endpoint the ESP32 timing modules dial into
    (`docs/track_setup/en/PROTOCOL.md`). Unlike every other route in this app there
    is no JWT here — the devices cannot reasonably do OAuth — so this is gated two
    ways instead: only served in [DeploymentMode.LOCAL], and only while the race
    device is actually configured for
    [io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceMode.ESP32_WEBSOCKET_DIRECT];
    the gateway itself then rejects any `device_id` outside its configured allowlist. */
fun Route.hardwareRoutes(
    gateway: ReconfigurableMeasurementGateway,
    deploymentMode: DeploymentMode,
) {
    webSocket("/hardware/esp32/ws") {
        if (deploymentMode != DeploymentMode.LOCAL) {
            close(reason = CloseReason(code = CloseReason.Codes.VIOLATED_POLICY, message = "Not a local deployment"))
            return@webSocket
        }
        val esp32Gateway = gateway.currentDelegate() as? Esp32WebSocketDirectMeasurementGateway
        if (esp32Gateway == null) {
            logger.warn { "Rejecting ESP32 connection: race device is not in ESP32_WEBSOCKET_DIRECT mode" }
            close(reason = CloseReason(code = CloseReason.Codes.VIOLATED_POLICY, message = "Not in ESP32 mode"))
            return@webSocket
        }
        esp32Gateway.handleConnection(session = KtorEsp32DeviceSession(session = this))
    }
}
