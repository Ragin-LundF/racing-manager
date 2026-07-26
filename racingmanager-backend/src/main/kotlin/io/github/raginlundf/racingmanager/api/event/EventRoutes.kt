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
import io.github.raginlundf.racingmanager.application.event.ActivateEventResult
import io.github.raginlundf.racingmanager.application.event.ArchiveEventResult
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.event.DeleteEventResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.event.ReactivateEventResult
import io.github.raginlundf.racingmanager.application.event.UpdateEventResult
import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.LaneType
import io.github.raginlundf.racingmanager.domain.event.MeasurementType
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.util.UUID

fun Route.eventRoutes(jwtService: JwtService, eventService: EventService, eventRepository: EventRepository) {
    eventQueryRoutes(jwtService = jwtService, eventService = eventService, eventRepository = eventRepository)
    eventCreateRoute(jwtService = jwtService, eventService = eventService)
    eventUpdateRoute(jwtService = jwtService, eventService = eventService, eventRepository = eventRepository)
    eventDeleteRoute(jwtService = jwtService, eventService = eventService, eventRepository = eventRepository)
    eventActivateRoute(jwtService = jwtService, eventService = eventService, eventRepository = eventRepository)
    eventArchiveRoute(jwtService = jwtService, eventService = eventService, eventRepository = eventRepository)
    eventReactivateRoute(jwtService = jwtService, eventService = eventService, eventRepository = eventRepository)
}

private fun Route.eventQueryRoutes(
    jwtService: JwtService,
    eventService: EventService,
    eventRepository: EventRepository,
) {
    get("/api/v1/events") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val events = eventService.findAllForTenant(tenantId = principal.tenantId)
        call.respond(message = events.map { it.toResponseModel() })
    }

    get("/api/v1/events/{id}") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val id = UUID.fromString(call.parameters["id"])
        val event = call.requireTenantEvent(
            principal = principal,
            eventId = id,
            eventRepository = eventRepository
        ) ?: return@get
        call.respond(message = event.toResponseModel())
    }
}

private fun Route.eventCreateRoute(jwtService: JwtService, eventService: EventService) {
    post("/api/v1/events") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val request = call.receive<CreateEventRequestModel>()

        val settings = EventSettings(
            laneType = runCatching {
                LaneType.valueOf(request.laneType)
            }.getOrDefault(defaultValue = LaneType.TWO_LANE),
            measurementType = runCatching {
                MeasurementType.valueOf(request.measurementType)
            }.getOrDefault(defaultValue = MeasurementType.SIMULATED),
            maxParticipants = request.maxParticipants,
            // ponytail: a non-positive length is meaningless, treat it as unset rather than a 400
            trackLength = request.trackLength?.takeIf { it > 0 },
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
}

private fun Route.eventUpdateRoute(
    jwtService: JwtService,
    eventService: EventService,
    eventRepository: EventRepository,
) {
    put("/api/v1/events/{id}") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@put
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@put
        val id = UUID.fromString(call.parameters["id"])
        call.requireTenantEvent(principal = principal, eventId = id, eventRepository = eventRepository) ?: return@put
        val request = call.receive<UpdateEventRequestModel>()

        val settings = EventSettings(
            laneType = runCatching {
                LaneType.valueOf(request.laneType)
            }.getOrDefault(defaultValue = LaneType.TWO_LANE),
            measurementType = runCatching {
                MeasurementType.valueOf(request.measurementType)
            }.getOrDefault(defaultValue = MeasurementType.SIMULATED),
            maxParticipants = request.maxParticipants,
            // ponytail: a non-positive length is meaningless, treat it as unset rather than a 400
            trackLength = request.trackLength?.takeIf { it > 0 },
        )

        val result = eventService.update(
            id = id,
            name = request.name,
            description = request.description,
            settings = settings,
            expectedVersion = request.expectedVersion,
            actorId = principal.userId
        )
        call.respondEventUpdate(result)
    }
}

private suspend fun ApplicationCall.respondEventUpdate(result: UpdateEventResult) {
    when (result) {
        is UpdateEventResult.Success -> {
            respond(message = result.event.toResponseModel())
        }

        is UpdateEventResult.NotFound -> {
            respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"),
            )
        }

        is UpdateEventResult.CannotModifyFinishedEvent -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "CANNOT_MODIFY_FINISHED_EVENT",
                    message = "Cannot modify a completed or archived event"
                ),
            )
        }

        is UpdateEventResult.Conflict -> {
            respond(
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
            respond(
                status = HttpStatusCode.Locked,
                message = ErrorResponseModel(
                    code = "EVENT_LOCKED_FOR_SYNC",
                    message = "Event is checked out to a local instance and locked until results are synced back"
                ),
            )
        }
    }
}

private fun Route.eventDeleteRoute(
    jwtService: JwtService,
    eventService: EventService,
    eventRepository: EventRepository,
) {
    delete("/api/v1/events/{id}") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@delete
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@delete
        val id = UUID.fromString(call.parameters["id"])
        call.requireTenantEvent(
            principal = principal,
            eventId = id,
            eventRepository = eventRepository
        ) ?: return@delete

        when (eventService.delete(id = id, actorId = principal.userId)) {
            is DeleteEventResult.Success -> call.respond(status = HttpStatusCode.NoContent, message = Unit)
            is DeleteEventResult.NotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"),
            )
        }
    }
}

private fun Route.eventActivateRoute(
    jwtService: JwtService,
    eventService: EventService,
    eventRepository: EventRepository,
) {
    post("/api/v1/events/{id}/activate") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val id = UUID.fromString(call.parameters["id"])
        val event = call.requireTenantEvent(
            principal = principal,
            eventId = id,
            eventRepository = eventRepository
        ) ?: return@post

        // The event's own version, not a hardcoded 0 — a draft that was edited or
        // stood down by a newer event is past version 0 and must still be activatable.
        val activateResult = eventService.activate(
            id = id,
            expectedVersion = event.version,
            actorId = principal.userId,
        )
        when (val result = activateResult) {
            is ActivateEventResult.Success -> {
                call.respond(message = result.event.toResponseModel())
            }

            is ActivateEventResult.NotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"),
                )
            }

            is ActivateEventResult.InvalidStatus -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "INVALID_STATUS",
                        message = "Event must be in DRAFT status to activate"
                    ),
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
}

private fun Route.eventArchiveRoute(
    jwtService: JwtService,
    eventService: EventService,
    eventRepository: EventRepository,
) {
    post("/api/v1/events/{id}/archive") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val id = UUID.fromString(call.parameters["id"])
        call.requireTenantEvent(
            principal = principal,
            eventId = id,
            eventRepository = eventRepository
        ) ?: return@post

        when (val result = eventService.archive(id, principal.userId)) {
            is ArchiveEventResult.Success -> {
                call.respond(message = result.event.toResponseModel())
            }

            is ArchiveEventResult.NotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"),
                )
            }

            is ArchiveEventResult.InvalidStatus -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "INVALID_STATUS",
                        message = "Event must be in ACTIVE status to archive"
                    ),
                )
            }
        }
    }
}

private fun Route.eventReactivateRoute(
    jwtService: JwtService,
    eventService: EventService,
    eventRepository: EventRepository,
) {
    post("/api/v1/events/{id}/reactivate") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val id = UUID.fromString(call.parameters["id"])
        call.requireTenantEvent(principal = principal, eventId = id, eventRepository = eventRepository) ?: return@post

        when (val result = eventService.reactivate(id = id, actorId = principal.userId)) {
            is ReactivateEventResult.Success -> {
                call.respond(message = result.event.toResponseModel())
            }

            is ReactivateEventResult.NotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"),
                )
            }

            is ReactivateEventResult.InvalidStatus -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "INVALID_STATUS",
                        message = "Event must be in ARCHIVED status to reactivate"
                    ),
                )
            }
        }
    }
}

private fun EventEntity.toResponseModel(): EventResponseModel {
    return EventResponseModel(
        id = id.toString(),
        name = name,
        description = description,
        status = status.name,
        settings = EventSettingsResponseModel(
            laneType = settings.laneType.name,
            measurementType = settings.measurementType.name,
            maxParticipants = settings.maxParticipants,
            trackLength = settings.trackLength,
        ),
        version = version,
        createdBy = createdBy.toString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt?.toString(),
        activatedAt = activatedAt?.toString(),
    )
}
