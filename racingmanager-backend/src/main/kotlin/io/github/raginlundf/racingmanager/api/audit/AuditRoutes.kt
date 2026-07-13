package io.github.raginlundf.racingmanager.api.audit

import io.github.raginlundf.racingmanager.api.audit.models.AuditEntryResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.api.requireTenantEvent
import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.util.UUID

/** Audit review is admin-only, not the general `rm:user` operational bar —
    it exposes actor-level history across the tenant. `/api/v1/audit` (global,
    no event scoping) is filtered to the caller's tenant via a join through
    the audited action's actor (design Q12/§7, Slice E). */
fun Route.auditRoutes(jwtService: JwtService, auditService: AuditService, eventRepository: EventRepository) {
    get("/api/v1/events/{eventId}/audit") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@get
        val entries = auditService.findByEventId(eventId)
        call.respond(entries.map { it.toResponseModel() })
    }

    get("/api/v1/audit") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN)) return@get
        val action = call.request.queryParameters["action"]
        val targetType = call.request.queryParameters["targetType"]
        val targetId = call.request.queryParameters["targetId"]?.let { UUID.fromString(it) }
        val actorId = call.request.queryParameters["actorId"]?.let { UUID.fromString(it) }
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
        val entries = auditService.query(action, targetType, targetId, actorId, principal.tenantId, limit, offset)
        call.respond(entries.map { it.toResponseModel() })
    }
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
