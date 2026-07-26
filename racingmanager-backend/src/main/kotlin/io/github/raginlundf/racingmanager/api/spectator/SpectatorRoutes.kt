package io.github.raginlundf.racingmanager.api.spectator

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.api.requireTenantEvent
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorEventModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorExchangeRequestModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorExchangeResponseModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorHeatModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorKnockoutMatchModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorKnockoutRoundModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorKnockoutStateModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorLaneModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorParticipantStandingModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorRankingEntryModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorSnapshotResponseModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorTokenResponseModel
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.application.spectator.SpectatorKnockoutMatch
import io.github.raginlundf.racingmanager.application.spectator.SpectatorKnockoutRound
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
import io.github.raginlundf.racingmanager.application.spectator.SpectatorSnapshot
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking
import io.github.raginlundf.racingmanager.domain.spectator.SpectatorExchangeCodeEntity
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SpectatorExchangeCodeRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.spectator.SpectatorWebSocketService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@Serializable
private data class WsAuthMessage(val type: String? = null, val token: String)

private val SPECTATOR_TOKEN_TTL = 4.hours
private val EXCHANGE_CODE_TTL = 5.minutes
private val SPECTATOR_ELIGIBLE_STATUSES = setOf(EventStatus.ACTIVE, EventStatus.ARCHIVED)

/** Token-bound spectator access (design §F): replaces the formerly-open
`/api/v1/public` surface. An operator exchanges an event for a one-time
code (never the raw JWT); the spectator UI trades that code for an
`rm:spectator` JWT which is bound to exactly one `event_id` — every read
route below derives the event from the token, never from a URL parameter,
so there is no "switch event via URL" surface to attack in the first place. */
fun Route.spectatorRoutes(
    jwtService: JwtService,
    spectatorService: SpectatorService,
    eventRepository: EventRepository,
    webSocketService: SpectatorWebSocketService,
    exchangeCodeRepository: SpectatorExchangeCodeRepository,
) {
    val clock = Clock.System

    spectatorTokenRoute(
        jwtService = jwtService,
        eventRepository = eventRepository,
        exchangeCodeRepository = exchangeCodeRepository,
        clock = clock,
    )
    spectatorExchangeRoute(jwtService = jwtService, exchangeCodeRepository = exchangeCodeRepository, clock = clock)
    spectatorSnapshotRoute(jwtService = jwtService, spectatorService = spectatorService)
    spectatorLiveRoute(jwtService = jwtService, webSocketService = webSocketService)
}

private fun Route.spectatorTokenRoute(
    jwtService: JwtService,
    eventRepository: EventRepository,
    exchangeCodeRepository: SpectatorExchangeCodeRepository,
    clock: Clock,
) {
    post("/api/v1/events/{eventId}/spectator-token") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        val event = call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        if (event.status !in SPECTATOR_ELIGIBLE_STATUSES) {
            call.respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "EVENT_NOT_ELIGIBLE",
                    message = "Event must be ACTIVE or ARCHIVED for spectator access"
                ),
            )
            return@post
        }

        val now = clock.now()
        val token = jwtService.issueAccessToken(
            userId = UUID.randomUUID(),
            tenantId = principal.tenantId,
            scopes = setOf(Scopes.SPECTATOR),
            eventId = eventId,
            ttl = SPECTATOR_TOKEN_TTL,
        )
        val code = UUID.randomUUID()
        exchangeCodeRepository.insert(
            entry = SpectatorExchangeCodeEntity(
                id = code,
                tenantId = principal.tenantId,
                eventId = eventId,
                token = token,
                createdAt = now,
                expiresAt = now.plus(duration = EXCHANGE_CODE_TTL),
            ),
        )
        call.respond(
            status = HttpStatusCode.Created,
            message = SpectatorTokenResponseModel(
                exchangeCode = code.toString(),
                expiresIn = EXCHANGE_CODE_TTL.inWholeSeconds
            ),
        )
    }
}

private fun Route.spectatorExchangeRoute(
    jwtService: JwtService,
    exchangeCodeRepository: SpectatorExchangeCodeRepository,
    clock: Clock,
) {
    post("/api/v1/spectator/exchange") {
        val request = call.receive<SpectatorExchangeRequestModel>()
        val code = runCatching { UUID.fromString(request.code) }.getOrNull()
        if (code == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponseModel(code = "INVALID_CODE", message = "Malformed exchange code")
            )
            return@post
        }
        val entry = exchangeCodeRepository.consume(id = code, now = clock.now())
        if (entry == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponseModel(
                    code = "INVALID_CODE",
                    message = "Exchange code is invalid, expired, or already used"
                )
            )
            return@post
        }
        val principal = jwtService.verifyAccessToken(token = entry.token)
        val expiresIn = principal?.let { (it.expiresAt - clock.now()).inWholeSeconds } ?: 0L
        call.respond(
            SpectatorExchangeResponseModel(
                accessToken = entry.token,
                expiresIn = expiresIn,
                eventId = entry.eventId.toString(),
            ),
        )
    }
}

private fun Route.spectatorSnapshotRoute(jwtService: JwtService, spectatorService: SpectatorService) {
    get("/api/v1/spectator/snapshot") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.SPECTATOR)) return@get
        val eventId = principal.eventId
        if (eventId == null) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorResponseModel(code = "FORBIDDEN", message = "Not a spectator token")
            )
            return@get
        }
        val snapshot = spectatorService.getSnapshot(eventId = eventId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found")
            )
        call.respond(snapshot.toResponseModel())
    }
}

private fun Route.spectatorLiveRoute(jwtService: JwtService, webSocketService: SpectatorWebSocketService) {
    webSocket("/api/v1/spectator/live") {
        runCatching {
            val eventId = resolveSpectatorEventId(jwtService = jwtService) ?: return@webSocket

            webSocketService.addConnection(eventId = eventId, session = this)
            try {
                closeReason.await()
            } finally {
                webSocketService.removeConnection(eventId = eventId, session = this)
            }
        }
    }
}

private suspend fun DefaultWebSocketServerSession.resolveSpectatorEventId(jwtService: JwtService): UUID? {
    val json = Json { ignoreUnknownKeys = true }

    val authFrame = withTimeoutOrNull(5_000.milliseconds) { incoming.receive() } as? Frame.Text
    val token = authFrame?.let {
        runCatching { json.decodeFromString<WsAuthMessage>(string = it.readText()).token }.getOrNull()
    }
    if (token == null) {
        close(reason = CloseReason(code = CloseReason.Codes.VIOLATED_POLICY, message = "Access token required"))
        return null
    }
    val principal = jwtService.verifyAccessToken(token = token)
    if (principal == null || !principal.hasAnyScope(Scopes.SPECTATOR)) {
        close(
            reason = CloseReason(
                code = CloseReason.Codes.VIOLATED_POLICY,
                message = "Invalid or insufficient spectator token"
            )
        )
        return null
    }
    val eventId = principal.eventId
    if (eventId == null) {
        close(reason = CloseReason(code = CloseReason.Codes.VIOLATED_POLICY, message = "Not a spectator token"))
        return null
    }
    return eventId
}

internal fun SpectatorSnapshot.toResponseModel(): SpectatorSnapshotResponseModel {
    return SpectatorSnapshotResponseModel(
        event = SpectatorEventModel(
            id = event.id.toString(),
            name = event.name,
            description = event.description,
            status = event.status.name,
            laneType = event.settings.laneType.name,
            measurementType = event.settings.measurementType.name,
            trackLength = event.settings.trackLength,
        ),
        currentHeat = currentHeat?.toResponseModel(),
        upcomingHeats = upcomingHeats.map { it.toResponseModel() },
        qualificationRankings = qualificationRankings.map { it.toResponseModel() },
        qualificationStatus = qualificationStatus,
        knockout = knockout?.let { k ->
            SpectatorKnockoutStateModel(
                status = k.status,
                pairingMode = k.pairingMode,
                rounds = k.rounds.map { r: SpectatorKnockoutRound ->
                    SpectatorKnockoutRoundModel(
                        roundNumber = r.roundNumber,
                        matches = r.matches.map { m: SpectatorKnockoutMatch ->
                            SpectatorKnockoutMatchModel(
                                id = m.id.toString(),
                                roundNumber = m.roundNumber,
                                matchNumber = m.matchNumber,
                                participant1Id = m.participant1Id?.toString(),
                                participant2Id = m.participant2Id?.toString(),
                                winnerId = m.winnerId?.toString(),
                                status = m.status,
                                isBye = m.isBye,
                            )
                        },
                    )
                },
            )
        },
        knockoutStandings = knockoutStandings.map { s ->
            SpectatorParticipantStandingModel(
                participantId = s.participantId.toString(),
                startNumber = s.startNumber,
                firstName = s.firstName,
                lastName = s.lastName,
                bestQualificationTimeNanos = s.bestQualificationTimeNanos,
                bestKnockoutTimeNanos = s.bestKnockoutTimeNanos,
                state = s.state,
                place = s.place,
                racing = s.racing,
            )
        },
    )
}

private fun HeatEntity.toResponseModel(): SpectatorHeatModel {
    val finishedMeasurements =
        measurements.filter { it.outcome == LaneOutcome.FINISHED || it.outcome == LaneOutcome.DNF }
    return SpectatorHeatModel(
        id = id.toString(),
        heatNumber = heatNumber,
        round = round,
        status = status.name,
        lanes = lanes.map { lane ->
            val measurement = measurements.firstOrNull { it.lane == lane.lane }
            SpectatorLaneModel(
                lane = lane.lane,
                participantId = lane.participantId.toString(),
                participantStartNumber = lane.participantStartNumber,
                participantFirstName = lane.participantFirstName,
                participantLastName = lane.participantLastName,
                durationNanos = measurement?.durationNanos,
                outcome = measurement?.outcome?.name,
            )
        },
        hasResult = finishedMeasurements.isNotEmpty(),
    )
}

private fun QualificationRanking.toResponseModel(): SpectatorRankingEntryModel {
    return SpectatorRankingEntryModel(
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
