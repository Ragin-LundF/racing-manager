package io.github.raginlundf.racingmanager.api.sync

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.api.sync.models.PairRequestModel
import io.github.raginlundf.racingmanager.api.sync.models.PairResponseModel
import io.github.raginlundf.racingmanager.api.sync.models.PairedInstanceResponseModel
import io.github.raginlundf.racingmanager.api.sync.models.PairingTokenResponseModel
import io.github.raginlundf.racingmanager.api.sync.models.SyncResultsRequestModel
import io.github.raginlundf.racingmanager.api.sync.models.SyncResultsResponseModel
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.application.sync.PairResult
import io.github.raginlundf.racingmanager.application.sync.PairingTokenResult
import io.github.raginlundf.racingmanager.application.sync.RevokeResult
import io.github.raginlundf.racingmanager.application.sync.SyncResultsResult
import io.github.raginlundf.racingmanager.application.sync.SyncService
import io.github.raginlundf.racingmanager.domain.sync.PairedInstanceEntity
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.UUID

/** Hosted-side pairing and sync-back APIs (design §I) — all gated to
`hosted` mode and the caller's own tenant, except `/pair` itself, which a
fresh local instance calls before it has any tenant-scoped credential at
all; the one-time pairing code is what authorizes it, not a token. */
fun Route.syncRoutes(jwtService: JwtService, syncService: SyncService, deploymentMode: DeploymentMode) {
    post("/api/v1/tenant/local-instances/pairing-token") {
        if (deploymentMode != DeploymentMode.HOSTED) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorResponseModel(code = "NOT_HOSTED", message = "Pairing is only available in hosted mode")
            )
            return@post
        }
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN)) return@post

        when (val result = syncService.issuePairingToken(tenantId = principal.tenantId)) {
            is PairingTokenResult.Success -> call.respond(
                status = HttpStatusCode.Created,
                message = PairingTokenResponseModel(pairingCode = result.code.toString(), expiresIn = result.expiresIn),
            )
        }
    }

    post("/api/v1/local-instances/pair") {
        val request = call.receive<PairRequestModel>()
        val pairingCode = runCatching { UUID.fromString(request.pairingCode) }.getOrNull()
        val localInstanceId = runCatching { UUID.fromString(request.localInstanceId) }.getOrNull()
        if (pairingCode == null || localInstanceId == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponseModel(
                    code = "INVALID_REQUEST",
                    message = "Malformed pairing code or instance id"
                )
            )
            return@post
        }

        when (val result = syncService.pair(pairingCode = pairingCode, localInstanceId = localInstanceId)) {
            is PairResult.Success -> call.respond(
                status = HttpStatusCode.Created,
                message = PairResponseModel(
                    localInstanceId = result.instance.id.toString(),
                    tenantId = result.instance.tenantId.toString()
                ),
            )

            is PairResult.InvalidOrExpiredCode -> call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponseModel(
                    code = "INVALID_CODE",
                    message = "Pairing code is invalid, expired, or already used"
                ),
            )
        }
    }

    get("/api/v1/tenant/local-instances") {
        if (deploymentMode != DeploymentMode.HOSTED) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorResponseModel(
                    code = "NOT_HOSTED",
                    message = "Local instances are only visible in hosted mode"
                )
            )
            return@get
        }
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN)) return@get
        call.respond(syncService.listInstances(tenantId = principal.tenantId).map { it.toResponseModel() })
    }

    post("/api/v1/tenant/local-instances/{id}/revoke") {
        if (deploymentMode != DeploymentMode.HOSTED) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorResponseModel(
                    code = "NOT_HOSTED",
                    message = "Local instances are only managed in hosted mode"
                )
            )
            return@post
        }
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN)) return@post
        val instanceId = UUID.fromString(call.parameters["id"])

        when (syncService.revoke(tenantId = principal.tenantId, instanceId = instanceId)) {
            is RevokeResult.Success -> call.respond(status = HttpStatusCode.NoContent, message = Unit)
            is RevokeResult.NotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "INSTANCE_NOT_FOUND", message = "Local instance not found")
            )
        }
    }

    post("/api/v1/tenant/local-instances/{id}/sync-results") {
        if (deploymentMode != DeploymentMode.HOSTED) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorResponseModel(code = "NOT_HOSTED", message = "Results are synced back in hosted mode")
            )
            return@post
        }
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN)) return@post
        val instanceId = UUID.fromString(call.parameters["id"])
        val request = call.receive<SyncResultsRequestModel>()
        val eventId = runCatching { UUID.fromString(request.eventId) }.getOrNull()
        if (eventId == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponseModel(code = "INVALID_REQUEST", message = "Malformed eventId")
            )
            return@post
        }
        val resultsJson = Json.encodeToString(serializer = JsonElement.serializer(), value = request.results)

        when (val result =
            syncService.syncResults(
                tenantId = principal.tenantId,
                instanceId = instanceId,
                eventId = eventId,
                resultsJson = resultsJson,
                actorId = principal.userId
            )) {
            is SyncResultsResult.Success -> call.respond(
                status = HttpStatusCode.Created,
                message = SyncResultsResponseModel(
                    syncedResultId = result.syncedResultId.toString(),
                    eventId = eventId.toString(),
                    status = "SYNCED"
                ),
            )

            is SyncResultsResult.InstanceNotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "INSTANCE_NOT_FOUND", message = "Local instance not found")
            )

            is SyncResultsResult.InstanceRevoked -> call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorResponseModel(
                    code = "INSTANCE_REVOKED",
                    message = "This local instance has been revoked"
                )
            )

            is SyncResultsResult.EventNotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found")
            )

            is SyncResultsResult.EventNotLocked -> call.respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "EVENT_NOT_LOCKED",
                    message = "Event was never checked out for local execution"
                )
            )
        }
    }
}

private fun PairedInstanceEntity.toResponseModel(): PairedInstanceResponseModel {
    return PairedInstanceResponseModel(
        id = id.toString(),
        status = status.name,
        pairedAt = pairedAt.toString(),
        lastSyncAt = lastSyncAt?.toString(),
    )
}
