package io.github.raginlundf.racingmanager.api.racedevice

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.racedevice.models.RaceDeviceSettingsResponseModel
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
        val settings = parseSettings(
            mode = request.mode,
            endpoint = request.endpoint,
            finishTimeoutMs = request.finishTimeoutMs,
        ) ?: return@put call.respondValidationError()
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
                val result = RaceDeviceProbe.testConnection(endpoint = request.endpoint)
                call.respond(TestRaceDeviceResponseModel(ok = result.ok, pingMs = result.pingMs, error = result.error))
            }
        }
    }
}

/** Validates and builds settings from request fields; null on any invalid input.
    The endpoint is only required to be a WebSocket URL when hardware is selected —
    switching back to the simulator keeps the last endpoint around. */
private fun parseSettings(mode: String, endpoint: String, finishTimeoutMs: Long): RaceDeviceSettings? {
    val parsedMode = runCatching { RaceDeviceMode.from(value = mode) }.getOrNull() ?: return null
    if (finishTimeoutMs <= 0) return null
    if (parsedMode == RaceDeviceMode.HARDWARE && !isValidEndpoint(endpoint = endpoint)) return null
    return RaceDeviceSettings(mode = parsedMode, endpoint = endpoint, finishTimeoutMs = finishTimeoutMs)
}

private fun isValidEndpoint(endpoint: String): Boolean {
    return endpoint.startsWith(prefix = "ws://") || endpoint.startsWith(prefix = "wss://")
}

private fun RaceDeviceSettings.toResponseModel(): RaceDeviceSettingsResponseModel {
    return RaceDeviceSettingsResponseModel(mode = mode.name, endpoint = endpoint, finishTimeoutMs = finishTimeoutMs)
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
