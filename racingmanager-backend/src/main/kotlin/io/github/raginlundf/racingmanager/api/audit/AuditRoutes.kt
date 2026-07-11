package io.github.raginlundf.racingmanager.api.audit

import io.github.raginlundf.racingmanager.api.audit.models.AuditEntryResponseModel
import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SessionResult
import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.util.UUID

fun Route.auditRoutes(authService: AuthService, auditService: AuditService) {
    get("/api/v1/events/{eventId}/audit") {
        val session = authenticateRequest(call, authService) ?: return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        val entries = auditService.findByEventId(eventId)
        call.respond(entries.map { it.toResponseModel() })
    }

    get("/api/v1/audit") {
        val session = authenticateRequest(call, authService) ?: return@get
        val action = call.request.queryParameters["action"]
        val targetType = call.request.queryParameters["targetType"]
        val targetId = call.request.queryParameters["targetId"]?.let { UUID.fromString(it) }
        val actorId = call.request.queryParameters["actorId"]?.let { UUID.fromString(it) }
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
        val entries = auditService.query(action, targetType, targetId, actorId, limit, offset)
        call.respond(entries.map { it.toResponseModel() })
    }
}

private suspend fun authenticateRequest(call: ApplicationCall, authService: AuthService): SessionResult.Valid? {
    val sessionId = call.request.headers["X-Session-Id"]
        ?: return null.also {
            call.respond(
                status = HttpStatusCode.Unauthorized,
                message = ErrorResponseModel("MISSING_SESSION", "Session ID is required"),
            )
        }

    val result = authService.getSession(UUID.fromString(sessionId))
    if (result !is SessionResult.Valid) {
        call.respond(
            status = HttpStatusCode.Unauthorized,
            message = ErrorResponseModel("SESSION_EXPIRED", "Session has expired"),
        )
        return null
    }
    return result
}

private fun AuditEntryEntity.toResponseModel() = AuditEntryResponseModel(
    id = id.toString(),
    actorId = actorId?.toString(),
    action = action,
    targetType = targetType,
    targetId = targetId?.toString(),
    summary = summary,
    details = details,
    correlationId = correlationId,
    occurredAt = occurredAt.toString(),
)
