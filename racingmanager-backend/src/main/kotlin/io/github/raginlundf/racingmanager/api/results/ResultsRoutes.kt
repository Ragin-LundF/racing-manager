package io.github.raginlundf.racingmanager.api.results

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
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
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SessionResult
import io.github.raginlundf.racingmanager.application.knockout.KnockoutResultEntry
import io.github.raginlundf.racingmanager.application.results.BackupExport
import io.github.raginlundf.racingmanager.application.results.CompleteEventResult
import io.github.raginlundf.racingmanager.application.results.EventResultSnapshot
import io.github.raginlundf.racingmanager.application.results.JsonExport
import io.github.raginlundf.racingmanager.application.results.ReopenEventResult
import io.github.raginlundf.racingmanager.application.results.RestoreResult
import io.github.raginlundf.racingmanager.application.results.ResultsService
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.Measurement
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Route.resultsRoutes(authService: AuthService, resultsService: ResultsService) {
    get("/api/v1/events/{eventId}/results/snapshot") {
        val session = authenticateRequest(call, authService) ?: return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        val snapshot = resultsService.getSnapshot(eventId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"),
            )
        call.respond(snapshot.toResponseModel())
    }

    post("/api/v1/events/{eventId}/results/complete") {
        val session = authenticateRequest(call, authService) ?: return@post
        val eventId = UUID.fromString(call.parameters["eventId"])

        when (val result = resultsService.completeEvent(eventId, session.user.id)) {
            is CompleteEventResult.Success -> {
                call.respond(status = HttpStatusCode.OK, message = ErrorResponseModel("OK", "Event completed"))
            }
            is CompleteEventResult.NotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"))
            }
            is CompleteEventResult.InvalidStatus -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INVALID_STATUS", "Event must be ACTIVE"))
            }
        }
    }

    post("/api/v1/events/{eventId}/results/reopen") {
        val session = authenticateRequest(call, authService) ?: return@post
        val eventId = UUID.fromString(call.parameters["eventId"])

        when (val result = resultsService.reopenEvent(eventId, session.user.id)) {
            is ReopenEventResult.Success -> {
                call.respond(status = HttpStatusCode.OK, message = ErrorResponseModel("OK", "Event reopened"))
            }
            is ReopenEventResult.NotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"))
            }
            is ReopenEventResult.InvalidStatus -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INVALID_STATUS", "Event must be COMPLETED"))
            }
        }
    }

    get("/api/v1/events/{eventId}/results/csv") {
        val session = authenticateRequest(call, authService) ?: return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        val result = resultsService.exportCsv(eventId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"),
            )
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${result.filename}\"")
        call.respondText(result.csv, ContentType.Text.Plain)
    }

    get("/api/v1/events/{eventId}/results/html") {
        val session = authenticateRequest(call, authService) ?: return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        val locale = call.request.headers["Accept-Language"]?.take(2) ?: "en"
        val result = resultsService.exportHtml(eventId, locale)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"),
            )
        call.respondText(result.html, ContentType.Text.Html)
    }

    get("/api/v1/events/{eventId}/results/json") {
        val session = authenticateRequest(call, authService) ?: return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        val result = resultsService.exportJson(eventId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"),
            )
        call.respond(result.toResponseModel())
    }

    get("/api/v1/events/{eventId}/results/backup") {
        val session = authenticateRequest(call, authService) ?: return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        val result = resultsService.exportBackup(eventId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"),
            )
        call.respond(result.toResponseModel())
    }

    post("/api/v1/events/{eventId}/results/restore") {
        val session = authenticateRequest(call, authService) ?: return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        val backup = call.receive<BackupResponseModel>()

        when (val result = resultsService.restoreFromBackup(eventId, backup, session.user.id)) {
            is RestoreResult.Success -> {
                call.respond(status = HttpStatusCode.OK, message = result.toResponseModel())
            }
            is RestoreResult.NotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"))
            }
            is RestoreResult.InvalidStatus -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INVALID_STATUS", "Event must be ACTIVE"))
            }
            is RestoreResult.SnapshotMismatch -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("SNAPSHOT_MISMATCH", "Backup does not match this event"))
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

private fun EventResultSnapshot.toResponseModel() = EventResultSnapshotResponseModel(
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

private fun QualificationRanking.toResponseModel() = QualificationResultEntryModel(
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

private fun KnockoutResultEntry.toResponseModel() = KnockoutResultEntryModel(
    rank = rank,
    participantId = participantId.toString(),
    firstName = firstName,
    lastName = lastName,
    startNumber = startNumber,
    club = club,
)

private fun HeatEntity.toResponseModel() = HeatResultEntryModel(
    id = id.toString(),
    round = round,
    heatNumber = heatNumber,
    status = status.name,
    lanes = lanes.map { it.toResponseModel() },
    measurements = measurements.map { it.toResponseModel() },
    startedAt = startedAt?.toString(),
    finishedAt = finishedAt?.toString(),
)

private fun HeatLaneAssignment.toResponseModel() = HeatResultLaneModel(
    lane = lane,
    participantId = participantId.toString(),
    participantStartNumber = participantStartNumber,
    participantFirstName = participantFirstName,
    participantLastName = participantLastName,
)

private fun Measurement.toResponseModel() = HeatResultMeasurementModel(
    id = id.toString(),
    lane = lane,
    durationNanos = durationNanos,
    outcome = outcome.name,
    receivedAt = receivedAt.toString(),
)

private fun io.github.raginlundf.racingmanager.application.results.JsonExport.toResponseModel() = JsonExportResponseModel(
    schemaVersion = schemaVersion,
    exportedAt = exportedAt,
    event = snapshot.toResponseModel(),
)

private fun io.github.raginlundf.racingmanager.application.results.BackupExport.toResponseModel() = BackupResponseModel(
    schemaVersion = schemaVersion,
    exportedAt = exportedAt,
    event = snapshot.toResponseModel(),
)

private fun RestoreResult.Success.toResponseModel() = RestoreResponseModel(
    eventId = event.id.toString(),
    name = event.name,
    status = event.status.name,
)
