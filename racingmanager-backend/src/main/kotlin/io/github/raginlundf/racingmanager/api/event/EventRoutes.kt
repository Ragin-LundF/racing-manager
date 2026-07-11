package io.github.raginlundf.racingmanager.api.event

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.event.models.ConflictResponseModel
import io.github.raginlundf.racingmanager.api.event.models.CreateEventRequestModel
import io.github.raginlundf.racingmanager.api.event.models.EventResponseModel
import io.github.raginlundf.racingmanager.api.event.models.EventSettingsResponseModel
import io.github.raginlundf.racingmanager.api.event.models.UpdateEventRequestModel
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SessionResult
import io.github.raginlundf.racingmanager.application.event.ActivateEventResult
import io.github.raginlundf.racingmanager.application.event.ArchiveEventResult
import io.github.raginlundf.racingmanager.application.event.CreateEventResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.event.UpdateEventResult
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.LaneType
import io.github.raginlundf.racingmanager.domain.event.MeasurementType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.util.UUID

fun Route.eventRoutes(authService: AuthService, eventService: EventService) {
    post("/api/v1/events") {
        val session = authenticateRequest(call, authService) ?: return@post
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
            actorId = session.user.id,
        )

        call.respond(
            status = HttpStatusCode.Created,
            message = (result as CreateEventResult.Success).event.toResponseModel(),
        )
    }

    get("/api/v1/events") {
        val session = authenticateRequest(call, authService) ?: return@get
        val events = eventService.findAll()
        call.respond(events.map { it.toResponseModel() })
    }

    get("/api/v1/events/{id}") {
        val session = authenticateRequest(call, authService) ?: return@get
        val id = UUID.fromString(call.parameters["id"])
        val event = eventService.findById(id)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"),
            )
        call.respond(event.toResponseModel())
    }

    put("/api/v1/events/{id}") {
        val session = authenticateRequest(call, authService) ?: return@put
        val id = UUID.fromString(call.parameters["id"])
        val request = call.receive<UpdateEventRequestModel>()

        val settings = EventSettings(
            laneType = try { LaneType.valueOf(request.laneType) } catch (_: IllegalArgumentException) { LaneType.TWO_LANE },
            measurementType = try { MeasurementType.valueOf(request.measurementType) } catch (_: IllegalArgumentException) { MeasurementType.SIMULATED },
            maxParticipants = request.maxParticipants,
        )

        when (val result = eventService.update(id, request.name, request.description, settings, request.expectedVersion, session.user.id)) {
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
        }
    }

    post("/api/v1/events/{id}/activate") {
        val session = authenticateRequest(call, authService) ?: return@post
        val id = UUID.fromString(call.parameters["id"])

        when (val result = eventService.activate(id, 0L, session.user.id)) {
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
        val session = authenticateRequest(call, authService) ?: return@post
        val id = UUID.fromString(call.parameters["id"])

        when (val result = eventService.archive(id, session.user.id)) {
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
