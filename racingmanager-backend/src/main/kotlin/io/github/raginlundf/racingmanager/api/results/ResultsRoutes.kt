package io.github.raginlundf.racingmanager.api.results

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.results.models.BackupResponseModel
import io.github.raginlundf.racingmanager.api.results.models.EventResultSnapshotResponseModel
import io.github.raginlundf.racingmanager.api.results.models.EventResultSummaryModel
import io.github.raginlundf.racingmanager.api.results.models.HeatResultEntryModel
import io.github.raginlundf.racingmanager.api.results.models.HeatResultLaneModel
import io.github.raginlundf.racingmanager.api.results.models.HeatResultMeasurementModel
import io.github.raginlundf.racingmanager.api.results.models.JsonExportResponseModel
import io.github.raginlundf.racingmanager.api.results.models.KnockoutResultEntryModel
import io.github.raginlundf.racingmanager.api.results.models.QualificationResultEntryModel
import io.github.raginlundf.racingmanager.api.results.models.RestoreResponseModel
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.api.requireTenantEvent
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.event.CompleteEventResult
import io.github.raginlundf.racingmanager.application.event.ReopenEventResult
import io.github.raginlundf.racingmanager.application.knockout.KnockoutResultEntry
import io.github.raginlundf.racingmanager.application.results.BackupExport
import io.github.raginlundf.racingmanager.application.results.EventResultSnapshot
import io.github.raginlundf.racingmanager.application.results.JsonExport
import io.github.raginlundf.racingmanager.application.results.RestoreResult
import io.github.raginlundf.racingmanager.application.results.ResultsService
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.Measurement
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Route.resultsRoutes(jwtService: JwtService, resultsService: ResultsService, eventService: EventService, eventRepository: EventRepository) {
    get("/api/v1/events/{eventId}/results/snapshot") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@get
        val snapshot = resultsService.getSnapshot(eventId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"),
            )
        call.respond(snapshot.toResponseModel())
    }

    post("/api/v1/events/{eventId}/results/complete") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@post

        when (val result = eventService.completeEvent(eventId, principal.userId)) {
            is CompleteEventResult.Success -> {
                call.respond(status = HttpStatusCode.OK, message = ErrorResponseModel(code = "OK", message = "Event completed"))
            }
            is CompleteEventResult.NotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"))
            }
            is CompleteEventResult.InvalidStatus -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel(code = "INVALID_STATUS", message = "Event must be ACTIVE"))
            }
        }
    }

    post("/api/v1/events/{eventId}/results/reopen") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@post

        when (val result = eventService.reopenEvent(eventId, principal.userId)) {
            is ReopenEventResult.Success -> {
                call.respond(status = HttpStatusCode.OK, message = ErrorResponseModel(code = "OK", message = "Event reopened"))
            }
            is ReopenEventResult.NotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"))
            }
            is ReopenEventResult.InvalidStatus -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel(code = "INVALID_STATUS", message = "Event must be COMPLETED"))
            }
        }
    }

    get("/api/v1/events/{eventId}/results/csv") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@get
        val result = resultsService.exportCsv(eventId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"),
            )
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${result.filename}\"")
        call.respondText(result.csv, ContentType.Text.Plain)
    }

    get("/api/v1/events/{eventId}/results/html") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@get
        val locale = call.request.headers["Accept-Language"]?.take(2) ?: "en"
        val result = resultsService.exportHtml(eventId, locale)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"),
            )
        call.respondText(result.html, ContentType.Text.Html)
    }

    get("/api/v1/events/{eventId}/results/json") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@get
        val result = resultsService.exportJson(eventId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"),
            )
        call.respond(result.toResponseModel())
    }

    get("/api/v1/events/{eventId}/results/backup") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@get
        val result = resultsService.exportBackup(eventId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"),
            )
        call.respond(result.toResponseModel())
    }

    post("/api/v1/events/{eventId}/results/restore") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(principal, eventId, eventRepository) ?: return@post
        val backup = call.receive<BackupResponseModel>()

        when (val result = resultsService.restoreFromBackup(eventId, backup, principal.userId)) {
            is RestoreResult.Success -> {
                call.respond(status = HttpStatusCode.OK, message = result.toResponseModel())
            }
            is RestoreResult.NotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found"))
            }
            is RestoreResult.InvalidStatus -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel(code = "INVALID_STATUS", message = "Event must be ACTIVE"))
            }
            is RestoreResult.SnapshotMismatch -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel(code = "SNAPSHOT_MISMATCH", message = "Backup does not match this event"))
            }
        }
    }
}

private fun EventResultSnapshot.toResponseModel(): EventResultSnapshotResponseModel {
    return EventResultSnapshotResponseModel(
        event = EventResultSummaryModel(
            id = event.id.toString(),
            name = event.name,
            description = event.description,
            status = event.status.name,
            laneType = event.settings.laneType.name,
            measurementType = event.settings.measurementType.name,
            createdAt = event.createdAt.toString(),
            activatedAt = event.activatedAt?.toString(),
        ),
        qualificationRankings = qualificationRankings.map { it.toResponseModel() },
        knockoutResults = knockoutResults.map { it.toResponseModel() },
        allHeats = allHeats.map { it.toResponseModel() },
        measurementType = event.settings.measurementType.name,
        isSimulated = isSimulated,
    )
}

private fun QualificationRanking.toResponseModel(): QualificationResultEntryModel {
    return QualificationResultEntryModel(
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

private fun KnockoutResultEntry.toResponseModel(): KnockoutResultEntryModel {
    return KnockoutResultEntryModel(
        rank = rank,
        participantId = participantId.toString(),
        firstName = firstName,
        lastName = lastName,
        startNumber = startNumber,
        club = club,
    )
}

private fun HeatEntity.toResponseModel(): HeatResultEntryModel {
    return HeatResultEntryModel(
        id = id.toString(),
        round = round,
        heatNumber = heatNumber,
        status = status.name,
        lanes = lanes.map { it.toResponseModel() },
        measurements = measurements.map { it.toResponseModel() },
        startedAt = startedAt?.toString(),
        finishedAt = finishedAt?.toString(),
    )
}

private fun HeatLaneAssignment.toResponseModel(): HeatResultLaneModel {
    return HeatResultLaneModel(
        lane = lane,
        participantId = participantId.toString(),
        participantStartNumber = participantStartNumber,
        participantFirstName = participantFirstName,
        participantLastName = participantLastName,
    )
}

private fun Measurement.toResponseModel(): HeatResultMeasurementModel {
    return HeatResultMeasurementModel(
        id = id.toString(),
        lane = lane,
        durationNanos = durationNanos,
        outcome = outcome.name,
        receivedAt = receivedAt.toString(),
    )
}

private fun io.github.raginlundf.racingmanager.application.results.JsonExport.toResponseModel(): JsonExportResponseModel {
    return JsonExportResponseModel(
        schemaVersion = schemaVersion,
        exportedAt = exportedAt,
        event = snapshot.toResponseModel(),
    )
}

private fun io.github.raginlundf.racingmanager.application.results.BackupExport.toResponseModel(): BackupResponseModel {
    return BackupResponseModel(
        schemaVersion = schemaVersion,
        exportedAt = exportedAt,
        event = snapshot.toResponseModel(),
    )
}

private fun RestoreResult.Success.toResponseModel(): RestoreResponseModel {
    return RestoreResponseModel(
        eventId = event.id.toString(),
        name = event.name,
        status = event.status.name,
    )
}
