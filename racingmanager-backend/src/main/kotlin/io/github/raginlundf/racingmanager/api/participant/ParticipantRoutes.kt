package io.github.raginlundf.racingmanager.api.participant

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.participant.models.CreateParticipantRequestModel
import io.github.raginlundf.racingmanager.api.participant.models.ImportCsvRequestModel
import io.github.raginlundf.racingmanager.api.participant.models.ImportErrorModel
import io.github.raginlundf.racingmanager.api.participant.models.ImportResponseModel
import io.github.raginlundf.racingmanager.api.participant.models.ParticipantResponseModel
import io.github.raginlundf.racingmanager.api.participant.models.RandomizeRequestModel
import io.github.raginlundf.racingmanager.api.participant.models.RandomizeResponseModel
import io.github.raginlundf.racingmanager.api.participant.models.UpdateParticipantRequestModel
import io.github.raginlundf.racingmanager.api.participant.models.VehicleResponseModel
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.api.requireTenantEvent
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.application.participant.CreateParticipantResult
import io.github.raginlundf.racingmanager.application.participant.ParticipantActionResult
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.participant.RandomizeResult
import io.github.raginlundf.racingmanager.application.participant.UpdateParticipantResult
import io.github.raginlundf.racingmanager.application.participant.ImportResult
import io.github.raginlundf.racingmanager.application.participant.CsvParticipantRow
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.util.UUID

fun Route.participantRoutes(jwtService: JwtService, participantService: ParticipantService, eventRepository: EventRepository) {
    get("/api/v1/events/{eventId}/participants") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@get
        val participants = participantService.findByEventId(eventId)
        call.respond(participants.map { it.toResponseModel() })
    }

    get("/api/v1/events/{eventId}/participants/{id}") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@get
        val id = UUID.fromString(call.parameters["id"])
        val participant = participantService.findById(id)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel("PARTICIPANT_NOT_FOUND", "Participant not found"),
            )
        call.respond(participant.toResponseModel())
    }

    post("/api/v1/events/{eventId}/participants") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@post
        val request = call.receive<CreateParticipantRequestModel>()

        when (val result = participantService.create(
            eventId = eventId,
            startNumber = request.startNumber,
            firstName = request.firstName,
            lastName = request.lastName,
            club = request.club,
            vehicleName = request.vehicleName,
            vehicleCategory = request.vehicleCategory,
            actorId = principal.userId,
        )) {
            is CreateParticipantResult.Success -> {
                call.respond(status = HttpStatusCode.Created, message = result.participant.toResponseModel())
            }
            is CreateParticipantResult.EventNotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"))
            }
            is CreateParticipantResult.EventNotActive -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("EVENT_NOT_ACTIVE", "Event must be active"))
            }
            is CreateParticipantResult.DuplicateStartNumber -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("DUPLICATE_START_NUMBER", "Start number ${result.startNumber} already exists"))
            }
        }
    }

    put("/api/v1/events/{eventId}/participants/{id}") {
        val principal = call.authenticateRequest(jwtService) ?: return@put
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@put
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@put
        val id = UUID.fromString(call.parameters["id"])
        val request = call.receive<UpdateParticipantRequestModel>()

        when (val result = participantService.update(
            id = id,
            startNumber = request.startNumber,
            firstName = request.firstName,
            lastName = request.lastName,
            club = request.club,
            actorId = principal.userId,
        )) {
            is UpdateParticipantResult.Success -> {
                call.respond(result.participant.toResponseModel())
            }
            is UpdateParticipantResult.NotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("PARTICIPANT_NOT_FOUND", "Participant not found"))
            }
            is UpdateParticipantResult.DuplicateStartNumber -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("DUPLICATE_START_NUMBER", "Start number ${result.startNumber} already exists"))
            }
        }
    }

    post("/api/v1/events/{eventId}/participants/{id}/deactivate") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@post
        val id = UUID.fromString(call.parameters["id"])

        when (val result = participantService.deactivate(id, principal.userId)) {
            is ParticipantActionResult.Success -> call.respond(result.participant.toResponseModel())
            is ParticipantActionResult.NotFound -> call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("PARTICIPANT_NOT_FOUND", "Participant not found"))
            is ParticipantActionResult.AlreadyInactive -> call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("ALREADY_INACTIVE", "Participant is already inactive"))
            is ParticipantActionResult.AlreadyActive -> call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("ALREADY_ACTIVE", "Participant is already active"))
        }
    }

    post("/api/v1/events/{eventId}/participants/{id}/reactivate") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@post
        val id = UUID.fromString(call.parameters["id"])

        when (val result = participantService.reactivate(id, principal.userId)) {
            is ParticipantActionResult.Success -> call.respond(result.participant.toResponseModel())
            is ParticipantActionResult.NotFound -> call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("PARTICIPANT_NOT_FOUND", "Participant not found"))
            is ParticipantActionResult.AlreadyInactive -> call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("ALREADY_INACTIVE", "Participant is already inactive"))
            is ParticipantActionResult.AlreadyActive -> call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("ALREADY_ACTIVE", "Participant is already active"))
        }
    }

    post("/api/v1/events/{eventId}/participants/randomize") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@post
        val request = call.receive<RandomizeRequestModel>()

        when (val result = participantService.randomize(eventId, principal.userId, request.force)) {
            is RandomizeResult.Success -> {
                call.respond(RandomizeResponseModel(seed = result.seed))
            }
            is RandomizeResult.EventNotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"))
            }
            is RandomizeResult.EventNotActive -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("EVENT_NOT_ACTIVE", "Event must be active"))
            }
            is RandomizeResult.AlreadyRandomized -> {
                call.respond(status = HttpStatusCode.Conflict, message = RandomizeResponseModel(seed = result.seed.seed, alreadyRandomized = true))
            }
        }
    }

    post("/api/v1/events/{eventId}/participants/import") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@post
        val request = call.receive<ImportCsvRequestModel>()

        val rows = request.rows.map { row ->
            CsvParticipantRow(
                startNumber = row.startNumber,
                firstName = row.firstName,
                lastName = row.lastName,
                club = row.club,
                vehicleName = row.vehicleName,
                vehicleCategory = row.vehicleCategory,
            )
        }

        when (val result = participantService.importCsv(eventId, rows, principal.userId)) {
            is ImportResult.Completed -> {
                call.respond(
                    ImportResponseModel(
                        created = result.created.size,
                        errors = result.errors.map { ImportErrorModel(it.rowIndex, it.message) },
                    ),
                )
            }
            is ImportResult.EventNotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"))
            }
            is ImportResult.EventNotActive -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("EVENT_NOT_ACTIVE", "Event must be active"))
            }
        }
    }
}

private fun io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity.toResponseModel() = ParticipantResponseModel(
    id = id.toString(),
    eventId = eventId.toString(),
    startNumber = startNumber,
    firstName = firstName,
    lastName = lastName,
    club = club,
    status = status.name,
    sortOrder = sortOrder,
    vehicle = vehicle?.let { VehicleResponseModel(id = it.id.toString(), name = it.name, category = it.category) },
    createdAt = createdAt.toString(),
    updatedAt = updatedAt?.toString(),
)
