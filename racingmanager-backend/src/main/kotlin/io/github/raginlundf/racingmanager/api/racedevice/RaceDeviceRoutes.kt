package io.github.raginlundf.racingmanager.api.racedevice

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.racedevice.models.ArduinoSettingsModel
import io.github.raginlundf.racingmanager.api.racedevice.models.RaceDeviceSettingsResponseModel
import io.github.raginlundf.racingmanager.api.racedevice.models.SerialPortModel
import io.github.raginlundf.racingmanager.api.racedevice.models.TestRaceDeviceRequestModel
import io.github.raginlundf.racingmanager.api.racedevice.models.TestRaceDeviceResponseModel
import io.github.raginlundf.racingmanager.api.racedevice.models.UpdateRaceDeviceSettingsRequestModel
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceMode
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceProbe
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceSettings
import io.github.raginlundf.racingmanager.infrastructure.gateway.ReconfigurableMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.ArduinoTwoLaneSettings
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.FinishSemantics
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.SerialPortDiscovery
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.TwoLaneSerialProbe
import io.github.raginlundf.racingmanager.infrastructure.repositories.RaceDeviceSettingsRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

/** Race-device configuration for local installs (design: the app is the WebSocket
    client to the Raspberry Pi). `rm:admin`-only and gated to [DeploymentMode.LOCAL]
    — a hosted deployment has no single local device to point at. Saving applies
    live via [ReconfigurableMeasurementGateway.reconfigure]; the test endpoint opens
    a throwaway connection so an operator can validate an address before saving. */
fun Route.raceDeviceRoutes(
    jwtService: JwtService,
    gateway: ReconfigurableMeasurementGateway,
    settingsRepository: RaceDeviceSettingsRepository,
    deploymentMode: DeploymentMode,
) {
    raceDeviceSettingsRoutes(jwtService, gateway, settingsRepository, deploymentMode)
    raceDeviceTestRoutes(jwtService, deploymentMode)
}

private fun Route.raceDeviceSettingsRoutes(
    jwtService: JwtService,
    gateway: ReconfigurableMeasurementGateway,
    settingsRepository: RaceDeviceSettingsRepository,
    deploymentMode: DeploymentMode,
) {
    get("/api/v1/racedevice/settings") {
        if (!call.requireLocalMode(deploymentMode)) return@get
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN)) return@get
        call.respond(gateway.current().toResponseModel())
    }

    put("/api/v1/racedevice/settings") {
        if (!call.requireLocalMode(deploymentMode)) return@put
        val principal = call.authenticateRequest(jwtService) ?: return@put
        if (!call.requireScope(principal, Scopes.ADMIN)) return@put
        val request = call.receive<UpdateRaceDeviceSettingsRequestModel>()
        val settings = parseSettings(request = request) ?: return@put call.respondValidationError()
        settingsRepository.save(settings = settings)
        gateway.reconfigure(newSettings = settings)
        call.respond(settings.toResponseModel())
    }
}

private fun Route.raceDeviceTestRoutes(
    jwtService: JwtService,
    deploymentMode: DeploymentMode,
) {
    post("/api/v1/racedevice/test") {
        if (!call.requireLocalMode(deploymentMode)) return@post
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN)) return@post
        val request = call.receive<TestRaceDeviceRequestModel>()
        val mode = runCatching { RaceDeviceMode.from(value = request.mode) }.getOrNull()
            ?: return@post call.respondValidationError()
        when (mode) {
            // The simulator is always available in-process; nothing to reach.
            RaceDeviceMode.SIMULATED -> call.respond(TestRaceDeviceResponseModel(ok = true))
            RaceDeviceMode.HARDWARE -> {
                if (!isValidEndpoint(endpoint = request.endpoint)) return@post call.respondValidationError()
                call.respondProbeResult(result = RaceDeviceProbe.testConnection(endpoint = request.endpoint))
            }

            RaceDeviceMode.ARDUINO_TWO_LANE -> {
                val arduino = request.arduino?.toSettings() ?: return@post call.respondValidationError()
                call.respondProbeResult(result = TwoLaneSerialProbe.testConnection(settings = arduino))
            }
        }
    }

    get("/api/v1/racedevice/serialports") {
        if (!call.requireLocalMode(deploymentMode)) return@get
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN)) return@get
        call.respond(
            SerialPortDiscovery.availablePorts().map { SerialPortModel(name = it.name, description = it.description) },
        )
    }
}

/** Validates and builds settings from the request; null on any invalid input. The
    mode-specific block is only validated when that mode is selected — switching
    back keeps the other block around so the operator does not retype it. */
private fun parseSettings(request: UpdateRaceDeviceSettingsRequestModel): RaceDeviceSettings? {
    val parsedMode = runCatching { RaceDeviceMode.from(value = request.mode) }.getOrNull() ?: return null
    if (request.finishTimeoutMs <= 0) return null
    if (parsedMode == RaceDeviceMode.HARDWARE && !isValidEndpoint(endpoint = request.endpoint)) return null
    val arduino = request.arduino?.toSettings()
    if (parsedMode == RaceDeviceMode.ARDUINO_TWO_LANE && arduino == null) return null
    return RaceDeviceSettings(
        mode = parsedMode,
        endpoint = request.endpoint,
        finishTimeoutMs = request.finishTimeoutMs,
        arduino = arduino,
    )
}

/** Null when the block is unusable: an unopenable port or a non-positive timeout
    would only surface as a broken device at race time. */
private fun ArduinoSettingsModel.toSettings(): ArduinoTwoLaneSettings? {
    if (portName.isBlank() || rawLogPath.isBlank()) return null
    if (baudRate <= 0 || readyTimeoutMs <= 0) return null
    if (falseStartWindowMs < 0) return null
    val semantics = runCatching { FinishSemantics.from(value = finishSemantics) }.getOrNull() ?: return null
    return ArduinoTwoLaneSettings(
        portName = portName,
        baudRate = baudRate,
        readyTimeoutMs = readyTimeoutMs,
        falseStartWindowMs = falseStartWindowMs,
        finishSemantics = semantics,
        rawLogPath = rawLogPath,
    )
}

private fun isValidEndpoint(endpoint: String): Boolean {
    return endpoint.startsWith(prefix = "ws://") || endpoint.startsWith(prefix = "wss://")
}

private suspend fun ApplicationCall.respondProbeResult(result: RaceDeviceProbe.ProbeResult) {
    respond(TestRaceDeviceResponseModel(ok = result.ok, pingMs = result.pingMs, error = result.error))
}

private fun RaceDeviceSettings.toResponseModel(): RaceDeviceSettingsResponseModel {
    return RaceDeviceSettingsResponseModel(
        mode = mode.name,
        endpoint = endpoint,
        finishTimeoutMs = finishTimeoutMs,
        arduino = arduino?.let {
            ArduinoSettingsModel(
                portName = it.portName,
                baudRate = it.baudRate,
                readyTimeoutMs = it.readyTimeoutMs,
                falseStartWindowMs = it.falseStartWindowMs,
                finishSemantics = it.finishSemantics.name,
                rawLogPath = it.rawLogPath,
            )
        },
    )
}

private suspend fun ApplicationCall.requireLocalMode(deploymentMode: DeploymentMode): Boolean {
    if (deploymentMode == DeploymentMode.LOCAL) return true
    respond(
        status = HttpStatusCode.Forbidden,
        message = ErrorResponseModel(
            code = "NOT_LOCAL",
            message = "Race-device configuration is only available in local mode",
        ),
    )
    return false
}

private suspend fun ApplicationCall.respondValidationError() {
    respond(
        status = HttpStatusCode.BadRequest,
        message = ErrorResponseModel(code = "INVALID_REQUEST", message = "Invalid race-device settings"),
    )
}
