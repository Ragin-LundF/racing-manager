package io.github.raginlundf.racingmanager.api.event

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.event.models.ConflictResponseModel
import io.github.raginlundf.racingmanager.api.event.models.CreateEventRequestModel
import io.github.raginlundf.racingmanager.api.event.models.EventResponseModel
import io.github.raginlundf.racingmanager.api.event.models.EventSettingsResponseModel
import io.github.raginlundf.racingmanager.api.event.models.UpdateEventRequestModel
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.api.requireTenantEvent
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.application.event.ActivateEventResult
import io.github.raginlundf.racingmanager.application.event.ArchiveEventResult
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.event.DeleteEventResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.event.ReactivateEventResult
import io.github.raginlundf.racingmanager.application.event.UpdateEventResult
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.LaneType
import io.github.raginlundf.racingmanager.domain.event.MeasurementType
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.util.UUID

fun Route.eventRoutes(jwtService: JwtService, eventService: EventService, eventRepository: EventRepository) {
    post("/api/v1/events") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val request = call.receive<CreateEventRequestModel>()

        val settings = EventSettings(
            laneType = try { LaneType.valueOf(request.laneType) } catch (_: IllegalArgumentException) { LaneType.TWO_LANE },
            measurementType = try { MeasurementType.valueOf(request.measurementType) } catch (_: IllegalArgumentException) { MeasurementType.SIMULATED },
            maxParticipants = request.maxParticipants,
        )

        val result = eventService.create(
            name = request.name,
            description = request.description,
            settings = settings,
            actorId = principal.userId,
            tenantId = principal.tenantId,
        )

        call.respond(
            status = HttpStatusCode.Created,
            message = (result as CreateEventResult.Success).event.toResponseModel(),
        )
    }

    get("/api/v1/events") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val events = eventService.findAllForTenant(principal.tenantId)
        call.respond(events.map { it.toResponseModel() })
    }

    get("/api/v1/events/{id}") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val id = UUID.fromString(call.parameters["id"])
        val event = call.requireTenantEvent(principal, id, eventRepository) ?: return@get
        call.respond(event.toResponseModel())
    }

    put("/api/v1/events/{id}") {
        val principal = call.authenticateRequest(jwtService) ?: return@put
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@put
        val id = UUID.fromString(call.parameters["id"])
        call.requireTenantEvent(principal, id, eventRepository) ?: return@put
        val request = call.receive<UpdateEventRequestModel>()

        val settings = EventSettings(
            laneType = try { LaneType.valueOf(request.laneType) } catch (_: IllegalArgumentException) { LaneType.TWO_LANE },
            measurementType = try { MeasurementType.valueOf(request.measurementType) } catch (_: IllegalArgumentException) { MeasurementType.SIMULATED },
            maxParticipants = request.maxParticipants,
        )

        when (val result = eventService.update(id, request.name, request.description, settings, request.expectedVersion, principal.userId)) {
            is UpdateEventResult.Success -> {
                call.respond(result.event.toResponseModel())
            }
            is UpdateEventResult.NotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"),
                )
            }
            is UpdateEventResult.CannotModifyActiveEvent -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel("CANNOT_MODIFY_ACTIVE_EVENT", "Cannot modify an event that is not in DRAFT status"),
                )
            }
            is UpdateEventResult.Conflict -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ConflictResponseModel(
                        code = "VERSION_CONFLICT",
                        message = "Event was modified by another user. Refresh and try again.",
                        expectedVersion = result.expected,
                        actualVersion = result.actual,
                    ),
                )
            }
            is UpdateEventResult.Locked -> {
                call.respond(
                    status = HttpStatusCode.Locked,
                    message = ErrorResponseModel("EVENT_LOCKED_FOR_SYNC", "Event is checked out to a local instance and locked until results are synced back"),
                )
            }
        }
    }

    delete("/api/v1/events/{id}") {
        val principal = call.authenticateRequest(jwtService) ?: return@delete
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@delete
        val id = UUID.fromString(call.parameters["id"])
        call.requireTenantEvent(principal, id, eventRepository) ?: return@delete

        when (eventService.delete(id, principal.userId)) {
            is DeleteEventResult.Success -> call.respond(status = HttpStatusCode.NoContent, message = Unit)
            is DeleteEventResult.NotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"),
            )
        }
    }

    post("/api/v1/events/{id}/activate") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val id = UUID.fromString(call.parameters["id"])
        call.requireTenantEvent(principal, id, eventRepository) ?: return@post

        when (val result = eventService.activate(id, 0L, principal.userId)) {
            is ActivateEventResult.Success -> {
                call.respond(result.event.toResponseModel())
            }
            is ActivateEventResult.NotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"),
                )
            }
            is ActivateEventResult.InvalidStatus -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel("INVALID_STATUS", "Event must be in DRAFT status to activate"),
                )
            }
            is ActivateEventResult.Conflict -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ConflictResponseModel(
                        code = "VERSION_CONFLICT",
                        message = "Event was modified by another user. Refresh and try again.",
                        expectedVersion = result.expected,
                        actualVersion = result.actual,
                    ),
                )
            }
        }
    }

    post("/api/v1/events/{id}/archive") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val id = UUID.fromString(call.parameters["id"])
        call.requireTenantEvent(principal, id, eventRepository) ?: return@post

        when (val result = eventService.archive(id, principal.userId)) {
            is ArchiveEventResult.Success -> {
                call.respond(result.event.toResponseModel())
            }
            is ArchiveEventResult.NotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"),
                )
            }
            is ArchiveEventResult.InvalidStatus -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel("INVALID_STATUS", "Event must be in ACTIVE status to archive"),
                )
            }
        }
    }

    post("/api/v1/events/{id}/reactivate") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val id = UUID.fromString(call.parameters["id"])
        call.requireTenantEvent(principal, id, eventRepository) ?: return@post

        when (val result = eventService.reactivate(id, principal.userId)) {
            is ReactivateEventResult.Success -> {
                call.respond(result.event.toResponseModel())
            }
            is ReactivateEventResult.NotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"),
                )
            }
            is ReactivateEventResult.InvalidStatus -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel("INVALID_STATUS", "Event must be in ARCHIVED status to reactivate"),
                )
            }
        }
    }
}

private fun io.github.raginlundf.racingmanager.domain.event.EventEntity.toResponseModel() = EventResponseModel(
    id = id.toString(),
    name = name,
    description = description,
    status = status.name,
    settings = EventSettingsResponseModel(
        laneType = settings.laneType.name,
        measurementType = settings.measurementType.name,
        maxParticipants = settings.maxParticipants,
    ),
    version = version,
    createdBy = createdBy.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt?.toString(),
    activatedAt = activatedAt?.toString(),
)
