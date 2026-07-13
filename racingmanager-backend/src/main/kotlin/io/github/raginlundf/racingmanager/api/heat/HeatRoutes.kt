package io.github.raginlundf.racingmanager.api.heat

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.heat.models.AddMeasurementRequestModel
import io.github.raginlundf.racingmanager.api.heat.models.CreateHeatRequestModel
import io.github.raginlundf.racingmanager.api.heat.models.HeatResponseModel
import io.github.raginlundf.racingmanager.api.heat.models.HeatStateChangeEvent
import io.github.raginlundf.racingmanager.api.heat.models.HeatLaneResponseModel
import io.github.raginlundf.racingmanager.api.heat.models.MeasurementResponseModel
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SessionResult
import io.github.raginlundf.racingmanager.application.heat.AcceptResult
import io.github.raginlundf.racingmanager.application.heat.AddMeasurementResult
import io.github.raginlundf.racingmanager.application.heat.ArmHeatResult
import io.github.raginlundf.racingmanager.application.heat.CancelHeatResult
import io.github.raginlundf.racingmanager.application.heat.FinishHeatResult
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.heat.HeatServiceEvent
import io.github.raginlundf.racingmanager.application.heat.RejectResult
import io.github.raginlundf.racingmanager.application.heat.RepeatHeatResult
import io.github.raginlundf.racingmanager.application.heat.StartHeatResult
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
private data class WsAuthMessage(val type: String? = null, val sessionId: String)

fun Route.heatRoutes(authService: AuthService, heatService: HeatService) {
    get("/api/v1/events/{eventId}/heats") {
        val session = call.authenticateRequest(authService) ?: return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        val heats = heatService.findByEventId(eventId)
        call.respond(heats.map { it.toResponseModel() })
    }

    get("/api/v1/events/{eventId}/heats/latest") {
        val session = call.authenticateRequest(authService) ?: return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        val heat = heatService.findLatestByEventId(eventId)
        if (heat == null) {
            call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("NO_HEAT", "No heat found"))
        } else {
            call.respond(heat.toResponseModel())
        }
    }

    get("/api/v1/events/{eventId}/heats/{id}") {
        val session = call.authenticateRequest(authService) ?: return@get
        val id = UUID.fromString(call.parameters["id"])
        val heat = heatService.findById(id)
        if (heat == null) {
            call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("HEAT_NOT_FOUND", "Heat not found"))
        } else {
            call.respond(heat.toResponseModel())
        }
    }

    post("/api/v1/events/{eventId}/heats") {
        val session = call.authenticateRequest(authService) ?: return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        val request = call.receive<CreateHeatRequestModel>()
        val participantIds = request.participantIds.map { UUID.fromString(it) }

        when (val result = heatService.create(eventId, participantIds, session.user.id)) {
            is io.github.raginlundf.racingmanager.application.heat.CreateHeatResult.Success -> {
                call.respond(status = HttpStatusCode.Created, message = result.heat.toResponseModel())
            }
            is io.github.raginlundf.racingmanager.application.heat.CreateHeatResult.EventNotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"))
            }
            is io.github.raginlundf.racingmanager.application.heat.CreateHeatResult.EventNotActive -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("EVENT_NOT_ACTIVE", "Event must be active"))
            }
            is io.github.raginlundf.racingmanager.application.heat.CreateHeatResult.ParticipantNotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("PARTICIPANT_NOT_FOUND", "Participant not found"))
            }
            is io.github.raginlundf.racingmanager.application.heat.CreateHeatResult.ParticipantNotActive -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("PARTICIPANT_NOT_ACTIVE", "Participant must be active"))
            }
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/arm") {
        val session = call.authenticateRequest(authService) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.arm(id, session.user.id)) {
            is ArmHeatResult.Success -> call.respond(result.heat.toResponseModel())
            is ArmHeatResult.NotFound -> call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("HEAT_NOT_FOUND", "Heat not found"))
            is ArmHeatResult.InvalidStatus -> call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INVALID_STATUS", "Invalid heat status: ${result.current}"))
            is ArmHeatResult.GatewayError -> call.respond(status = HttpStatusCode.InternalServerError, message = ErrorResponseModel("GATEWAY_ERROR", result.message))
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/start") {
        val session = call.authenticateRequest(authService) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.start(id, session.user.id)) {
            is StartHeatResult.Success -> call.respond(result.heat.toResponseModel())
            is StartHeatResult.NotFound -> call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("HEAT_NOT_FOUND", "Heat not found"))
            is StartHeatResult.InvalidStatus -> call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INVALID_STATUS", "Invalid heat status: ${result.current}"))
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/finish") {
        val session = call.authenticateRequest(authService) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.finish(id, session.user.id)) {
            is FinishHeatResult.Success -> call.respond(result.heat.toResponseModel())
            is FinishHeatResult.NotFound -> call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("HEAT_NOT_FOUND", "Heat not found"))
            is FinishHeatResult.InvalidStatus -> call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INVALID_STATUS", "Invalid heat status: ${result.current}"))
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/cancel") {
        val session = call.authenticateRequest(authService) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.cancel(id, session.user.id)) {
            is CancelHeatResult.Success -> call.respond(result.heat.toResponseModel())
            is CancelHeatResult.NotFound -> call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("HEAT_NOT_FOUND", "Heat not found"))
            is CancelHeatResult.InvalidStatus -> call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INVALID_STATUS", "Invalid heat status: ${result.current}"))
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/accept") {
        val session = call.authenticateRequest(authService) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.acceptResult(id, session.user.id)) {
            is AcceptResult.Success -> call.respond(mapOf("status" to "accepted"))
            is AcceptResult.NotFound -> call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("HEAT_NOT_FOUND", "Heat not found"))
            is AcceptResult.InvalidStatus -> call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INVALID_STATUS", "Invalid heat status: ${result.current}"))
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/reject") {
        val session = call.authenticateRequest(authService) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.rejectResult(id, session.user.id)) {
            is RejectResult.Success -> call.respond(mapOf("status" to "rejected"))
            is RejectResult.NotFound -> call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("HEAT_NOT_FOUND", "Heat not found"))
            is RejectResult.InvalidStatus -> call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INVALID_STATUS", "Invalid heat status: ${result.current}"))
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/repeat") {
        val session = call.authenticateRequest(authService) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.repeat(id, session.user.id)) {
            is RepeatHeatResult.Success -> call.respond(result.heat.toResponseModel())
            is RepeatHeatResult.NotFound -> call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("HEAT_NOT_FOUND", "Heat not found"))
        }
    }

    put("/api/v1/events/{eventId}/heats/{id}/measurements") {
        val session = call.authenticateRequest(authService) ?: return@put
        val id = UUID.fromString(call.parameters["id"])
        val request = call.receive<AddMeasurementRequestModel>()
        when (val result = heatService.addMeasurement(id, request.lane, request.durationNanos, LaneOutcome.valueOf(request.outcome), session.user.id)) {
            is AddMeasurementResult.Success -> call.respond(result.heat.toResponseModel())
            is AddMeasurementResult.NotFound -> call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("HEAT_NOT_FOUND", "Heat not found"))
            is AddMeasurementResult.InvalidStatus -> call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INVALID_STATUS", "Invalid heat status: ${result.current}"))
        }
    }

    webSocket("/api/v1/events/{eventId}/live") {
        runCatching {
            val json = Json { ignoreUnknownKeys = true }

            val authFrame = withTimeoutOrNull(5_000) { incoming.receive() } as? Frame.Text
            val sessionId = authFrame?.let {
                runCatching { json.decodeFromString<WsAuthMessage>(it.readText()).sessionId }.getOrNull()
            }
            if (sessionId == null) {
                close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "Session required"))
                return@webSocket
            }
            val session = authService.getSession(UUID.fromString(sessionId))
            if (session !is SessionResult.Valid) {
                close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "Invalid session"))
                return@webSocket
            }

            val job = launch {
                heatService.events
                    .catch { /* ignore */ }
                    .collect { event ->
                        val heat = when (event) {
                            is HeatServiceEvent.HeatCreated -> event.heat
                            is HeatServiceEvent.HeatStateChanged -> event.heat
                            is HeatServiceEvent.HeatResultAccepted -> heatService.findById(event.heatId)
                            is HeatServiceEvent.HeatResultRejected -> heatService.findById(event.heatId)
                        }
                        if (heat != null) {
                            val message = json.encodeToString(
                                HeatStateChangeEvent(
                                    type = event::class.simpleName ?: "UNKNOWN",
                                    heat = heat.toResponseModel(),
                                ),
                            )
                            runCatching { send(Frame.Text(message)) }
                        }
                    }
            }

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    if (text == "ping") {
                        send(Frame.Text("pong"))
                    }
                }
            }

            job.cancel()
        }
    }
}

private fun HeatEntity.toResponseModel() = HeatResponseModel(
    id = id.toString(),
    eventId = eventId.toString(),
    round = round,
    heatNumber = heatNumber,
    status = status.name,
    lanes = lanes.map { l ->
        HeatLaneResponseModel(
            lane = l.lane,
            participantId = l.participantId.toString(),
            participantStartNumber = l.participantStartNumber,
            participantFirstName = l.participantFirstName,
            participantLastName = l.participantLastName,
        )
    },
    measurements = measurements.map { m ->
        MeasurementResponseModel(
            id = m.id.toString(),
            heatId = m.heatId.toString(),
            lane = m.lane,
            durationNanos = m.durationNanos,
            outcome = m.outcome.name,
            receivedAt = m.receivedAt.toString(),
        )
    },
    createdAt = createdAt.toString(),
    armedAt = armedAt?.toString(),
    startedAt = startedAt?.toString(),
    finishedAt = finishedAt?.toString(),
)
