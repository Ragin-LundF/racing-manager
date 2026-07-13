package io.github.raginlundf.racingmanager.api.health

import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.health.models.BuildInfoResponseModel
import io.github.raginlundf.racingmanager.api.health.models.DatabaseHealthModel
import io.github.raginlundf.racingmanager.api.health.models.DiagnosticsResponseModel
import io.github.raginlundf.racingmanager.api.health.models.EventSummaryModel
import io.github.raginlundf.racingmanager.api.health.models.HealthResponseModel
import io.github.raginlundf.racingmanager.api.health.models.ReadinessCheckModel
import io.github.raginlundf.racingmanager.api.health.models.ReadinessResponseModel
import io.github.raginlundf.racingmanager.api.health.models.RecoveryActionResponseModel
import io.github.raginlundf.racingmanager.api.health.models.UnfinishedHeatModel
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.application.diagnostics.DiagnosticsService
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** `/health`, `/readiness`, `/build-info` are liveness/build metadata and stay
    public — standard liveness-probe convention, and they carry no tenant or
    operational data. `/diagnostics` and `/diagnostics/recover` expose
    cross-event operational internals and are gated to `rm:admin` (design §7:
    "operational diagnostics should not accidentally become available to every
    authenticated user") **and** scoped to `principal.tenantId` (design §J.1)
    — an admin in a hosted multi-tenant deployment sees and can only recover
    their own tenant's events/heats, never another tenant's. */
fun Route.healthRoutes(diagnosticsService: DiagnosticsService, jwtService: JwtService) {
    get("/api/v1/health") {
        val db = diagnosticsService.checkDatabase()
        val status = if (db.connected) "UP" else "DOWN"
        call.respond(
            HealthResponseModel(
                status = status,
                database = DatabaseHealthModel(connected = db.connected, pingMs = db.pingMs),
            ),
        )
    }

    get("/api/v1/readiness") {
        val db = diagnosticsService.checkDatabase()
        val checks = mutableListOf(
            ReadinessCheckModel(
                name = "database",
                status = if (db.connected) "UP" else "DOWN",
                error = if (db.connected) null else "Cannot connect to database",
            ),
        )
        val overall = if (checks.all { it.status == "UP" }) "UP" else "DOWN"
        call.respond(ReadinessResponseModel(status = overall, checks = checks))
    }

    get("/api/v1/diagnostics") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN)) return@get
        val bundle = diagnosticsService.getBundle(principal.tenantId)
        call.respond(
            DiagnosticsResponseModel(
                database = DatabaseHealthModel(connected = bundle.database.connected, pingMs = bundle.database.pingMs),
                events = EventSummaryModel(
                    total = bundle.events.total,
                    draft = bundle.events.draft,
                    active = bundle.events.active,
                    completed = bundle.events.completed,
                    archived = bundle.events.archived,
                    totalParticipants = bundle.events.totalParticipants,
                    totalHeats = bundle.events.totalHeats,
                ),
                unfinishedHeats = bundle.unfinishedHeats.map { uf ->
                    UnfinishedHeatModel(
                        heatId = uf.heat.id.toString(),
                        heatNumber = uf.heat.heatNumber,
                        round = uf.heat.round,
                        status = uf.heat.status.name,
                        eventId = uf.event.id.toString(),
                        eventName = uf.event.name,
                    )
                },
                version = bundle.version,
            ),
        )
    }

    post("/api/v1/diagnostics/recover") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN)) return@post
        val params = call.receiveParameters()
        val heatId = params["heatId"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing heatId")
        val action = params["action"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing action")
        val parsedId = runCatching { java.util.UUID.fromString(heatId) }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, "Invalid heatId")
        }
        val result = diagnosticsService.recoverHeat(parsedId, action, principal.tenantId)
            ?: return@post call.respond(HttpStatusCode.NotFound, "Heat not found")
        call.respond(RecoveryActionResponseModel(heatId = result.heatId.toString(), action = result.action))
    }

    get("/api/v1/build-info") {
        val pkg = BuildInfoResponseModel::class.java.`package`
        call.respond(
            BuildInfoResponseModel(
                name = pkg?.implementationTitle ?: "racingmanager",
                version = pkg?.implementationVersion ?: "1.0-SNAPSHOT",
            ),
        )
    }
}