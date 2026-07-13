package io.github.raginlundf.racingmanager.api.heat

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.heat.models.AddMeasurementRequestModel
import io.github.raginlundf.racingmanager.api.heat.models.CreateHeatRequestModel
import io.github.raginlundf.racingmanager.api.heat.models.HeatLaneResponseModel
import io.github.raginlundf.racingmanager.api.heat.models.HeatResponseModel
import io.github.raginlundf.racingmanager.api.heat.models.HeatStateChangeEvent
import io.github.raginlundf.racingmanager.api.heat.models.MeasurementResponseModel
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.api.requireTenantEvent
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.application.heat.AcceptResult
import io.github.raginlundf.racingmanager.application.heat.AddMeasurementResult
import io.github.raginlundf.racingmanager.application.heat.ArmHeatResult
import io.github.raginlundf.racingmanager.application.heat.CancelHeatResult
import io.github.raginlundf.racingmanager.application.heat.CreateHeatResult
import io.github.raginlundf.racingmanager.application.heat.FinishHeatResult
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.heat.HeatServiceEvent
import io.github.raginlundf.racingmanager.application.heat.RejectResult
import io.github.raginlundf.racingmanager.application.heat.RepeatHeatResult
import io.github.raginlundf.racingmanager.application.heat.StartHeatResult
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Serializable
private data class WsAuthMessage(val type: String? = null, val token: String)

fun Route.heatRoutes(jwtService: JwtService, heatService: HeatService, eventRepository: EventRepository) {
    get("/api/v1/events/{eventId}/heats") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@get
        val heats = heatService.findByEventId(eventId = eventId)
        call.respond(heats.map { it.toResponseModel() })
    }

    get("/api/v1/events/{eventId}/heats/latest") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@get
        val heat = heatService.findLatestByEventId(eventId = eventId)
        if (heat == null) {
            call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "NO_HEAT", message = "No heat found")
            )
        } else {
            call.respond(heat.toResponseModel())
        }
    }

    get("/api/v1/events/{eventId}/heats/{id}") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@get
        val id = UUID.fromString(call.parameters["id"])
        val heat = heatService.findById(id)
        if (heat == null) {
            call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "HEAT_NOT_FOUND", message = "Heat not found")
            )
        } else {
            call.respond(heat.toResponseModel())
        }
    }

    post("/api/v1/events/{eventId}/heats") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val request = call.receive<CreateHeatRequestModel>()
        val participantIds = request.participantIds.map { UUID.fromString(it) }

        when (val result = heatService.create(
            eventId = eventId,
            participantIds = participantIds,
            actorId = principal.userId
        )) {
            is CreateHeatResult.Success -> {
                call.respond(status = HttpStatusCode.Created, message = result.heat.toResponseModel())
            }

            is CreateHeatResult.EventNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found")
                )
            }

            is CreateHeatResult.EventNotActive -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(code = "EVENT_NOT_ACTIVE", message = "Event must be active")
                )
            }

            is CreateHeatResult.ParticipantNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "PARTICIPANT_NOT_FOUND", message = "Participant not found")
                )
            }

            is CreateHeatResult.ParticipantNotActive -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "PARTICIPANT_NOT_ACTIVE",
                        message = "Participant must be active"
                    )
                )
            }
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/arm") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.arm(id = id, actorId = principal.userId)) {
            is ArmHeatResult.Success -> call.respond(message = result.heat.toResponseModel())
            is ArmHeatResult.NotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "HEAT_NOT_FOUND", message = "Heat not found")
            )

            is ArmHeatResult.InvalidStatus -> call.respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "INVALID_STATUS",
                    message = "Invalid heat status: ${result.current}"
                )
            )

            is ArmHeatResult.GatewayError -> call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorResponseModel(code = "GATEWAY_ERROR", message = result.message)
            )
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/start") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.start(id = id, actorId = principal.userId)) {
            is StartHeatResult.Success -> call.respond(result.heat.toResponseModel())
            is StartHeatResult.NotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "HEAT_NOT_FOUND", message = "Heat not found")
            )

            is StartHeatResult.InvalidStatus -> call.respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "INVALID_STATUS",
                    message = "Invalid heat status: ${result.current}"
                )
            )
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/finish") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.finish(id = id, actorId = principal.userId)) {
            is FinishHeatResult.Success -> call.respond(message = result.heat.toResponseModel())
            is FinishHeatResult.NotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "HEAT_NOT_FOUND", message = "Heat not found")
            )

            is FinishHeatResult.InvalidStatus -> call.respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "INVALID_STATUS",
                    message = "Invalid heat status: ${result.current}"
                )
            )
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/cancel") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.cancel(id = id, actorId = principal.userId)) {
            is CancelHeatResult.Success -> call.respond(message = result.heat.toResponseModel())
            is CancelHeatResult.NotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "HEAT_NOT_FOUND", message = "Heat not found")
            )

            is CancelHeatResult.InvalidStatus -> call.respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "INVALID_STATUS",
                    message = "Invalid heat status: ${result.current}"
                )
            )
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/accept") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.acceptResult(id = id, actorId = principal.userId)) {
            is AcceptResult.Success -> call.respond(mapOf("status" to "accepted"))
            is AcceptResult.NotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "HEAT_NOT_FOUND", message = "Heat not found")
            )

            is AcceptResult.InvalidStatus -> call.respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "INVALID_STATUS",
                    message = "Invalid heat status: ${result.current}"
                )
            )
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/reject") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.rejectResult(id = id, actorId = principal.userId)) {
            is RejectResult.Success -> call.respond(mapOf("status" to "rejected"))
            is RejectResult.NotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "HEAT_NOT_FOUND", message = "Heat not found")
            )

            is RejectResult.InvalidStatus -> call.respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "INVALID_STATUS",
                    message = "Invalid heat status: ${result.current}"
                )
            )
        }
    }

    post("/api/v1/events/{eventId}/heats/{id}/repeat") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val id = UUID.fromString(call.parameters["id"])
        when (val result = heatService.repeat(id = id, actorId = principal.userId)) {
            is RepeatHeatResult.Success -> call.respond(result.heat.toResponseModel())
            is RepeatHeatResult.NotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "HEAT_NOT_FOUND", message = "Heat not found")
            )
        }
    }

    put("/api/v1/events/{eventId}/heats/{id}/measurements") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@put
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@put
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@put
        val id = UUID.fromString(call.parameters["id"])
        val request = call.receive<AddMeasurementRequestModel>()
        when (val result = heatService.addMeasurement(
            heatId = id,
            lane = request.lane,
            durationNanos = request.durationNanos,
            outcome = LaneOutcome.valueOf(request.outcome),
            actorId = principal.userId
        )) {
            is AddMeasurementResult.Success -> call.respond(result.heat.toResponseModel())
            is AddMeasurementResult.NotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "HEAT_NOT_FOUND", message = "Heat not found")
            )

            is AddMeasurementResult.InvalidStatus -> call.respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "INVALID_STATUS",
                    message = "Invalid heat status: ${result.current}"
                )
            )
        }
    }

    webSocket("/api/v1/events/{eventId}/live") {
        runCatching {
            val json = Json { ignoreUnknownKeys = true }

            val authFrame = withTimeoutOrNull(timeout = 5_000.milliseconds) { incoming.receive() } as? Frame.Text
            val token = authFrame?.let {
                runCatching { json.decodeFromString<WsAuthMessage>(it.readText()).token }.getOrNull()
            }
            if (token == null) {
                close(reason = CloseReason(code = VIOLATED_POLICY, message = "Access token required"))
                return@webSocket
            }
            val principal = jwtService.verifyAccessToken(token)
            if (principal == null) {
                close(reason = CloseReason(code = VIOLATED_POLICY, message = "Invalid access token"))
                return@webSocket
            }
            if (!principal.hasAnyScope(Scopes.ADMIN, Scopes.USER)) {
                close(reason = CloseReason(code = VIOLATED_POLICY, message = "Insufficient scope"))
                return@webSocket
            }
            val eventId = UUID.fromString(call.parameters["eventId"])
            if (eventRepository.findByIdForTenant(id = eventId, tenantId = principal.tenantId) == null) {
                close(
                    reason = CloseReason(
                        code = VIOLATED_POLICY,
                        message = "Event not found or not accessible"
                    )
                )
                return@webSocket
            }

            val job = launch {
                heatService.events
                    .collect { event ->
                        val heat = when (event) {
                            is HeatServiceEvent.HeatCreated -> event.heat
                            is HeatServiceEvent.HeatStateChanged -> event.heat
                            is HeatServiceEvent.HeatResultAccepted -> heatService.findById(event.heatId)
                            is HeatServiceEvent.HeatResultRejected -> heatService.findById(event.heatId)
                        }
                        if (heat != null) {
                            val message = json.encodeToString(
                                value = HeatStateChangeEvent(
                                    type = event::class.simpleName ?: "UNKNOWN",
                                    heat = heat.toResponseModel(),
                                ),
                            )
                            runCatching { send(frame = Frame.Text(text = message)) }
                        }
                    }
            }

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    if (text == "ping") {
                        send(frame = Frame.Text(text = "pong"))
                    }
                }
            }

            job.cancel()
        }
    }
}

private fun HeatEntity.toResponseModel(): HeatResponseModel {
    return HeatResponseModel(
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
}
