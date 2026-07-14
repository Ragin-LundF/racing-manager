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
import io.github.raginlundf.racingmanager.application.auth.RequestPrincipal
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
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.websocket.DefaultWebSocketServerSession
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
    heatCollectionRoutes(jwtService = jwtService, heatService = heatService, eventRepository = eventRepository)
    heatDetailRoutes(jwtService = jwtService, heatService = heatService, eventRepository = eventRepository)
    heatCreateRoutes(jwtService = jwtService, heatService = heatService, eventRepository = eventRepository)
    heatArmRoutes(jwtService = jwtService, heatService = heatService, eventRepository = eventRepository)
    heatStartRoutes(jwtService = jwtService, heatService = heatService, eventRepository = eventRepository)
    heatFinishRoutes(jwtService = jwtService, heatService = heatService, eventRepository = eventRepository)
    heatCancelRoutes(jwtService = jwtService, heatService = heatService, eventRepository = eventRepository)
    heatAcceptRoutes(jwtService = jwtService, heatService = heatService, eventRepository = eventRepository)
    heatRejectRoutes(jwtService = jwtService, heatService = heatService, eventRepository = eventRepository)
    heatRepeatRoutes(jwtService = jwtService, heatService = heatService, eventRepository = eventRepository)
    heatMeasurementRoutes(jwtService = jwtService, heatService = heatService, eventRepository = eventRepository)
    heatLiveRoutes(jwtService = jwtService, heatService = heatService, eventRepository = eventRepository)
}

private fun Route.heatCollectionRoutes(
    jwtService: JwtService,
    heatService: HeatService,
    eventRepository: EventRepository,
) {
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
}

private fun Route.heatDetailRoutes(
    jwtService: JwtService,
    heatService: HeatService,
    eventRepository: EventRepository,
) {
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
}

private fun Route.heatCreateRoutes(
    jwtService: JwtService,
    heatService: HeatService,
    eventRepository: EventRepository,
) {
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
        val result = heatService.create(
            eventId = eventId,
            participantIds = participantIds,
            actorId = principal.userId
        )
        call.respondCreateResult(result)
    }
}

private suspend fun ApplicationCall.respondCreateResult(result: CreateHeatResult) {
    when (result) {
        is CreateHeatResult.Success -> {
            respond(status = HttpStatusCode.Created, message = result.heat.toResponseModel())
        }

        is CreateHeatResult.EventNotFound -> {
            respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found")
            )
        }

        is CreateHeatResult.EventNotActive -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(code = "EVENT_NOT_ACTIVE", message = "Event must be active")
            )
        }

        is CreateHeatResult.ParticipantNotFound -> {
            respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "PARTICIPANT_NOT_FOUND", message = "Participant not found")
            )
        }

        is CreateHeatResult.ParticipantNotActive -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "PARTICIPANT_NOT_ACTIVE",
                    message = "Participant must be active"
                )
            )
        }
    }
}

private fun Route.heatArmRoutes(
    jwtService: JwtService,
    heatService: HeatService,
    eventRepository: EventRepository,
) {
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
}

private fun Route.heatStartRoutes(
    jwtService: JwtService,
    heatService: HeatService,
    eventRepository: EventRepository,
) {
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
}

private fun Route.heatFinishRoutes(
    jwtService: JwtService,
    heatService: HeatService,
    eventRepository: EventRepository,
) {
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
}

private fun Route.heatCancelRoutes(
    jwtService: JwtService,
    heatService: HeatService,
    eventRepository: EventRepository,
) {
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
}

private fun Route.heatAcceptRoutes(
    jwtService: JwtService,
    heatService: HeatService,
    eventRepository: EventRepository,
) {
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
}

private fun Route.heatRejectRoutes(
    jwtService: JwtService,
    heatService: HeatService,
    eventRepository: EventRepository,
) {
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
}

private fun Route.heatRepeatRoutes(
    jwtService: JwtService,
    heatService: HeatService,
    eventRepository: EventRepository,
) {
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
}

private fun Route.heatMeasurementRoutes(
    jwtService: JwtService,
    heatService: HeatService,
    eventRepository: EventRepository,
) {
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
        val result = heatService.addMeasurement(
            heatId = id,
            lane = request.lane,
            durationNanos = request.durationNanos,
            outcome = LaneOutcome.valueOf(request.outcome),
            actorId = principal.userId
        )
        call.respondMeasurementResult(result)
    }
}

private suspend fun ApplicationCall.respondMeasurementResult(result: AddMeasurementResult) {
    when (result) {
        is AddMeasurementResult.Success -> respond(result.heat.toResponseModel())
        is AddMeasurementResult.NotFound -> respond(
            status = HttpStatusCode.NotFound,
            message = ErrorResponseModel(code = "HEAT_NOT_FOUND", message = "Heat not found")
        )

        is AddMeasurementResult.InvalidStatus -> respond(
            status = HttpStatusCode.Conflict,
            message = ErrorResponseModel(
                code = "INVALID_STATUS",
                message = "Invalid heat status: ${result.current}"
            )
        )
    }
}

private fun Route.heatLiveRoutes(
    jwtService: JwtService,
    heatService: HeatService,
    eventRepository: EventRepository,
) {
    webSocket("/api/v1/events/{eventId}/live") {
        runCatching {
            val json = Json { ignoreUnknownKeys = true }
            authenticateLiveConnection(
                json = json,
                jwtService = jwtService,
                eventRepository = eventRepository,
            ) ?: return@webSocket

            val job = launch {
                heatService.events
                    .collect { event ->
                        sendHeatEvent(event = event, heatService = heatService, json = json)
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

private suspend fun DefaultWebSocketServerSession.authenticateLiveConnection(
    json: Json,
    jwtService: JwtService,
    eventRepository: EventRepository,
): RequestPrincipal? {
    val authFrame = withTimeoutOrNull(timeout = 5_000.milliseconds) { incoming.receive() } as? Frame.Text
    val token = authFrame?.let {
        runCatching { json.decodeFromString<WsAuthMessage>(it.readText()).token }.getOrNull()
    }
    if (token == null) {
        close(reason = CloseReason(code = VIOLATED_POLICY, message = "Access token required"))
        return null
    }
    val principal = jwtService.verifyAccessToken(token)
    if (principal == null) {
        close(reason = CloseReason(code = VIOLATED_POLICY, message = "Invalid access token"))
        return null
    }
    if (!principal.hasAnyScope(Scopes.ADMIN, Scopes.USER)) {
        close(reason = CloseReason(code = VIOLATED_POLICY, message = "Insufficient scope"))
        return null
    }
    val eventId = UUID.fromString(call.parameters["eventId"])
    if (eventRepository.findByIdForTenant(id = eventId, tenantId = principal.tenantId) == null) {
        close(
            reason = CloseReason(
                code = VIOLATED_POLICY,
                message = "Event not found or not accessible"
            )
        )
        return null
    }
    return principal
}

private suspend fun DefaultWebSocketServerSession.sendHeatEvent(
    event: HeatServiceEvent,
    heatService: HeatService,
    json: Json,
) {
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
