package io.github.raginlundf.racingmanager.api

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.application.auth.RequestPrincipal
import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import java.util.UUID

/** Route-capability helpers reading a resolved [RequestPrincipal] (design §7).
    Authentication (`authenticateRequest`) answers "is this token valid?";
    these answer "may this token perform this operation on this resource?" —
    call them after `authenticateRequest`, before touching application state. */

/** Declares the scopes sufficient for an operational route. Responds 403 and
    returns false if the principal has none of [scopes] (accounting for
    `rm:admin` implying `rm:user`, see [RequestPrincipal.hasAnyScope]). */
suspend fun ApplicationCall.requireScope(principal: RequestPrincipal, vararg scopes: String): Boolean {
    if (principal.hasAnyScope(*scopes)) return true
    respond(
        status = HttpStatusCode.Forbidden,
        message = ErrorResponseModel(code = "FORBIDDEN", message = "Insufficient scope for this operation"),
    )
    return false
}

/** Resource-ownership check for the tenant-owning aggregate root (design §7):
    a route check alone is necessary but not sufficient, so this re-derives
    ownership from the repository rather than trusting the caller's URL.
    Responds 404 if [eventId] doesn't exist for any tenant (no cross-tenant
    existence disclosure) or 403 if it belongs to a different tenant than
    [principal] — a token from tenant A must never read or modify tenant B's
    event by guessing or substituting an id. */
suspend fun ApplicationCall.requireTenantEvent(
    principal: RequestPrincipal,
    eventId: UUID,
    eventRepository: EventRepository,
): EventEntity? {
    val event = eventRepository.findById(eventId)
    if (event == null) {
        respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"))
        return null
    }
    if (event.tenantId != principal.tenantId) {
        respond(
            status = HttpStatusCode.Forbidden,
            message = ErrorResponseModel(code = "FORBIDDEN", message = "Event not found or not accessible"),
        )
        return null
    }
    return event
}
