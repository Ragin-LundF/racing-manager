package io.github.raginlundf.racingmanager.api.qualification

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.qualification.models.HeatLaneScheduleResponseModel
import io.github.raginlundf.racingmanager.api.qualification.models.HeatScheduleResponseModel
import io.github.raginlundf.racingmanager.api.qualification.models.MeasurementScheduleResponseModel
import io.github.raginlundf.racingmanager.api.qualification.models.QualificationProgressResponseModel
import io.github.raginlundf.racingmanager.api.qualification.models.QualificationRankingResponseModel
import io.github.raginlundf.racingmanager.api.qualification.models.QualificationResponseModel
import io.github.raginlundf.racingmanager.api.qualification.models.SetupQualificationRequestModel
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.api.requireTenantEvent
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.application.qualification.FinalizeResult
import io.github.raginlundf.racingmanager.application.qualification.GenerateScheduleResult
import io.github.raginlundf.racingmanager.application.qualification.QualificationProgress
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.application.qualification.ReopenResult
import io.github.raginlundf.racingmanager.application.qualification.SetupQualificationResult
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.Measurement
import io.github.raginlundf.racingmanager.domain.qualification.QualificationEntity
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Route.qualificationRoutes(
    jwtService: JwtService,
    qualificationService: QualificationService,
    eventRepository: EventRepository,
) {
    qualificationSetupRoutes(jwtService, qualificationService, eventRepository)
    qualificationScheduleRoutes(jwtService, qualificationService, eventRepository)
    qualificationReadRoutes(jwtService, qualificationService, eventRepository)
    qualificationFinalizeRoutes(jwtService, qualificationService, eventRepository)
    qualificationReopenRoutes(jwtService, qualificationService, eventRepository)
}

private fun Route.qualificationSetupRoutes(
    jwtService: JwtService,
    qualificationService: QualificationService,
    eventRepository: EventRepository,
) {
    post("/api/v1/events/{eventId}/qualification/setup") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@post
        val request = call.receive<SetupQualificationRequestModel>()

        when (val result = qualificationService.setup(eventId, request.numberOfRuns, principal.userId)) {
            is SetupQualificationResult.Success -> {
                call.respond(status = HttpStatusCode.Created, message = result.qualification.toResponseModel())
            }
            is SetupQualificationResult.EventNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"),
                )
            }
            is SetupQualificationResult.EventNotActive -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(code = "EVENT_NOT_ACTIVE", message = "Event must be ACTIVE"),
                )
            }
            is SetupQualificationResult.AlreadyExists -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "QUALIFICATION_ALREADY_EXISTS",
                        message = "Qualification already exists",
                    ),
                )
            }
            is SetupQualificationResult.NotEnoughParticipants -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "NOT_ENOUGH_PARTICIPANTS",
                        message = "At least 2 active participants required",
                    ),
                )
            }
        }
    }
}

private fun Route.qualificationScheduleRoutes(
    jwtService: JwtService,
    qualificationService: QualificationService,
    eventRepository: EventRepository,
) {
    post("/api/v1/events/{eventId}/qualification/schedule") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@post

        when (val result = qualificationService.generateSchedule(eventId, principal.userId)) {
            is GenerateScheduleResult.Success -> {
                call.respond(result.qualification.toResponseModel())
            }
            is GenerateScheduleResult.QualificationNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "QUALIFICATION_NOT_FOUND", message = "Qualification not found"),
                )
            }
            is GenerateScheduleResult.InvalidStatus -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(code = "INVALID_STATUS", message = "Qualification must be PENDING"),
                )
            }
            is GenerateScheduleResult.NotEnoughParticipants -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "NOT_ENOUGH_PARTICIPANTS",
                        message = "At least 2 active participants required",
                    ),
                )
            }
            is GenerateScheduleResult.HeatsAlreadyExist -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(code = "HEATS_ALREADY_EXIST", message = "Schedule already generated"),
                )
            }
        }
    }

    get("/api/v1/events/{eventId}/qualification/schedule") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@get
        val schedule = qualificationService.getSchedule(eventId)
        call.respond(schedule.map { it.toHeatResponseModel() })
    }
}

private fun Route.qualificationReadRoutes(
    jwtService: JwtService,
    qualificationService: QualificationService,
    eventRepository: EventRepository,
) {
    get("/api/v1/events/{eventId}/qualification") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@get
        val qualification = qualificationService.findByEventId(eventId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "QUALIFICATION_NOT_FOUND", message = "Qualification not found"),
            )
        call.respond(qualification.toResponseModel())
    }

    get("/api/v1/events/{eventId}/qualification/rankings") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@get
        val rankings = qualificationService.getRankings(eventId)
        call.respond(rankings.map { it.toResponseModel() })
    }

    get("/api/v1/events/{eventId}/qualification/progress") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@get
        val progress = qualificationService.getProgress(eventId)
        call.respond(progress.toResponseModel())
    }
}

private fun Route.qualificationFinalizeRoutes(
    jwtService: JwtService,
    qualificationService: QualificationService,
    eventRepository: EventRepository,
) {
    post("/api/v1/events/{eventId}/qualification/finalize") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@post

        when (val result = qualificationService.finalize(eventId, principal.userId)) {
            is FinalizeResult.Success -> {
                call.respond(
                    status = HttpStatusCode.OK,
                    message = ErrorResponseModel(code = "OK", message = "Qualification finalized"),
                )
            }
            is FinalizeResult.QualificationNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "QUALIFICATION_NOT_FOUND", message = "Qualification not found"),
                )
            }
            is FinalizeResult.InvalidStatus -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "INVALID_STATUS",
                        message = "Qualification must be SCHEDULED or IN_PROGRESS",
                    ),
                )
            }
            is FinalizeResult.IncompleteHeats -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "INCOMPLETE_HEATS",
                        message = "${result.count} heat(s) still incomplete",
                    ),
                )
            }
        }
    }
}

private fun Route.qualificationReopenRoutes(
    jwtService: JwtService,
    qualificationService: QualificationService,
    eventRepository: EventRepository,
) {
    post("/api/v1/events/{eventId}/qualification/reopen") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@post

        when (val result = qualificationService.reopen(eventId, principal.userId)) {
            is ReopenResult.Success -> {
                call.respond(
                    status = HttpStatusCode.OK,
                    message = ErrorResponseModel(code = "OK", message = "Qualification reopened"),
                )
            }
            is ReopenResult.QualificationNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "QUALIFICATION_NOT_FOUND", message = "Qualification not found"),
                )
            }
            is ReopenResult.InvalidStatus -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(code = "INVALID_STATUS", message = "Qualification must be FINALIZED"),
                )
            }
        }
    }
}

private fun QualificationEntity.toResponseModel(): QualificationResponseModel {
    return QualificationResponseModel(
        id = id.toString(),
        eventId = eventId.toString(),
        status = status.name,
        numberOfRuns = numberOfRuns,
        seed = seed,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt?.toString(),
        finalizedAt = finalizedAt?.toString(),
        finalizedBy = finalizedBy?.toString(),
    )
}

private fun QualificationRanking.toResponseModel(): QualificationRankingResponseModel {
    return QualificationRankingResponseModel(
        participantId = participantId.toString(),
        startNumber = startNumber,
        firstName = firstName,
        lastName = lastName,
        club = club,
        bestTimeNanos = bestTimeNanos,
        totalTimeNanos = totalTimeNanos,
        completedRuns = completedRuns,
        dnfCount = dnfCount,
        rank = rank,
    )
}

private fun QualificationProgress.toResponseModel(): QualificationProgressResponseModel {
    return QualificationProgressResponseModel(
        status = status.name,
        totalHeats = totalHeats,
        completedHeats = completedHeats,
        inProgressHeats = inProgressHeats,
        plannedHeats = plannedHeats,
        cancelledHeats = cancelledHeats,
        totalParticipants = totalParticipants,
        participantsWithResults = participantsWithResults,
    )
}

private fun HeatEntity.toHeatResponseModel(): HeatScheduleResponseModel {
    return HeatScheduleResponseModel(
        id = id.toString(),
        eventId = eventId.toString(),
        round = round,
        heatNumber = heatNumber,
        status = status.name,
        lanes = lanes.map { it.toResponseModel() },
        measurements = measurements.map { it.toResponseModel() },
        createdAt = createdAt.toString(),
        armedAt = armedAt?.toString(),
        startedAt = startedAt?.toString(),
        finishedAt = finishedAt?.toString(),
    )
}

private fun HeatLaneAssignment.toResponseModel(): HeatLaneScheduleResponseModel {
    return HeatLaneScheduleResponseModel(
        lane = lane,
        participantId = participantId.toString(),
        participantStartNumber = participantStartNumber,
        participantFirstName = participantFirstName,
        participantLastName = participantLastName,
    )
}

private fun Measurement.toResponseModel(): MeasurementScheduleResponseModel {
    return MeasurementScheduleResponseModel(
        id = id.toString(),
        heatId = heatId.toString(),
        lane = lane,
        durationNanos = durationNanos,
        outcome = outcome.name,
        receivedAt = receivedAt.toString(),
    )
}
