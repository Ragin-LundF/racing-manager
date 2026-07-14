package io.github.raginlundf.racingmanager.api.knockout

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.heat.models.HeatLaneResponseModel
import io.github.raginlundf.racingmanager.api.heat.models.HeatResponseModel
import io.github.raginlundf.racingmanager.api.heat.models.MeasurementResponseModel
import io.github.raginlundf.racingmanager.api.knockout.models.CreateHeatForMatchRequestModel
import io.github.raginlundf.racingmanager.api.knockout.models.KnockoutMatchResponseModel
import io.github.raginlundf.racingmanager.api.knockout.models.KnockoutResultEntryResponseModel
import io.github.raginlundf.racingmanager.api.knockout.models.KnockoutTournamentResponseModel
import io.github.raginlundf.racingmanager.api.knockout.models.QualifiedParticipantResponseModel
import io.github.raginlundf.racingmanager.api.knockout.models.RecordMatchResultRequestModel
import io.github.raginlundf.racingmanager.api.knockout.models.SetManualPairingsRequestModel
import io.github.raginlundf.racingmanager.api.knockout.models.SetupKnockoutRequestModel
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.api.requireTenantEvent
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.application.knockout.CreateHeatForMatchResult
import io.github.raginlundf.racingmanager.application.knockout.FinalizeKnockoutResult
import io.github.raginlundf.racingmanager.application.knockout.GeneratePairingsResult
import io.github.raginlundf.racingmanager.application.knockout.KnockoutResultEntry
import io.github.raginlundf.racingmanager.application.knockout.KnockoutService
import io.github.raginlundf.racingmanager.application.knockout.RecordMatchResult
import io.github.raginlundf.racingmanager.application.knockout.SetManualPairingsResult
import io.github.raginlundf.racingmanager.application.knockout.SetupKnockoutResult
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchEntity
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutTournamentEntity
import io.github.raginlundf.racingmanager.domain.knockout.PairingMode
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Route.knockoutRoutes(jwtService: JwtService, knockoutService: KnockoutService, eventRepository: EventRepository) {
    knockoutSetupRoute(jwtService, knockoutService, eventRepository)
    knockoutReadRoutes(jwtService, knockoutService, eventRepository)
    knockoutManualPairingsRoute(jwtService, knockoutService, eventRepository)
    knockoutPairingsRoute(jwtService, knockoutService, eventRepository)
    knockoutHeatRoute(jwtService, knockoutService, eventRepository)
    knockoutResultRoute(jwtService, knockoutService, eventRepository)
    knockoutFinalizeRoute(jwtService, knockoutService, eventRepository)
    knockoutMatchListRoutes(jwtService, knockoutService, eventRepository)
}

private fun Route.knockoutSetupRoute(
    jwtService: JwtService,
    knockoutService: KnockoutService,
    eventRepository: EventRepository,
) {
    post("/api/v1/events/{eventId}/knockout/setup") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val request = call.receive<SetupKnockoutRequestModel>()
        val pairingMode = runCatching { PairingMode.valueOf(request.pairingMode) }.getOrElse {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponseModel(code = "INVALID_PAIRING_MODE", message = "Invalid pairing mode")
            )
            return@post
        }
        val result = knockoutService.setup(
            eventId = eventId,
            pairingMode = pairingMode,
            actorId = principal.userId
        )
        call.respondSetupResult(result)
    }
}

private suspend fun ApplicationCall.respondSetupResult(result: SetupKnockoutResult) {
    when (result) {
        is SetupKnockoutResult.Success -> {
            respond(status = HttpStatusCode.Created, message = result.tournament.toResponseModel())
        }

        is SetupKnockoutResult.EventNotFound -> {
            respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found")
            )
        }

        is SetupKnockoutResult.EventNotActive -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(code = "EVENT_NOT_ACTIVE", message = "Event must be ACTIVE")
            )
        }

        is SetupKnockoutResult.AlreadyExists -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(code = "KNOCKOUT_ALREADY_EXISTS", message = "Knockout already exists")
            )
        }

        is SetupKnockoutResult.QualificationNotFinalized -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "QUALIFICATION_NOT_FINALIZED",
                    message = "Qualification must be finalized"
                )
            )
        }

        is SetupKnockoutResult.NotEnoughParticipants -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "NOT_ENOUGH_PARTICIPANTS",
                    message = "At least 2 participants required"
                )
            )
        }
    }
}

private fun Route.knockoutReadRoutes(
    jwtService: JwtService,
    knockoutService: KnockoutService,
    eventRepository: EventRepository,
) {
    get("/api/v1/events/{eventId}/knockout") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@get
        val tournament = knockoutService.findByEventId(eventId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "KNOCKOUT_NOT_FOUND", message = "Knockout not found"),
            )
        call.respond(message = tournament.toResponseModel())
    }

    get("/api/v1/events/{eventId}/knockout/qualified-participants") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@get
        val participants = knockoutService.getQualifiedParticipants(eventId)
        call.respond(message = participants.map { it.toQualifiedResponseModel() })
    }
}

private fun Route.knockoutManualPairingsRoute(
    jwtService: JwtService,
    knockoutService: KnockoutService,
    eventRepository: EventRepository,
) {
    post("/api/v1/events/{eventId}/knockout/manual-pairings") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val request = call.receive<SetManualPairingsRequestModel>()
        val pairings = request.pairings.map { p ->
            Pair(
                first = UUID.fromString(p.participant1Id),
                second = p.participant2Id?.let { UUID.fromString(it) }
            )
        }
        val result = knockoutService.setManualPairings(
            eventId = eventId,
            pairings = pairings,
            actorId = principal.userId
        )
        call.respondManualPairingsResult(result)
    }
}

private suspend fun ApplicationCall.respondManualPairingsResult(result: SetManualPairingsResult) {
    when (result) {
        is SetManualPairingsResult.Success -> {
            respond(message = result.tournament.toResponseModel())
        }

        is SetManualPairingsResult.TournamentNotFound -> {
            respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "KNOCKOUT_NOT_FOUND", message = "Knockout not found")
            )
        }

        is SetManualPairingsResult.InvalidStatus -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(code = "INVALID_STATUS", message = "Knockout must be PAIRING")
            )
        }

        is SetManualPairingsResult.PairingsAlreadyExist -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "PAIRINGS_ALREADY_EXIST",
                    message = "Pairings already generated"
                )
            )
        }

        is SetManualPairingsResult.NotEnoughParticipants -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "NOT_ENOUGH_PARTICIPANTS",
                    message = "At least 1 pairing required"
                )
            )
        }

        is SetManualPairingsResult.WrongPairingMode -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "WRONG_PAIRING_MODE",
                    message = "Knockout must be in MANUAL mode"
                )
            )
        }
    }
}

private fun Route.knockoutPairingsRoute(
    jwtService: JwtService,
    knockoutService: KnockoutService,
    eventRepository: EventRepository,
) {
    post("/api/v1/events/{eventId}/knockout/pairings") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val result = knockoutService.generatePairings(eventId = eventId, actorId = principal.userId)
        call.respondPairingsResult(result)
    }
}

private suspend fun ApplicationCall.respondPairingsResult(result: GeneratePairingsResult) {
    when (result) {
        is GeneratePairingsResult.Success -> {
            respond(message = result.tournament.toResponseModel())
        }

        is GeneratePairingsResult.TournamentNotFound -> {
            respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "KNOCKOUT_NOT_FOUND", message = "Knockout not found")
            )
        }

        is GeneratePairingsResult.InvalidStatus -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(code = "INVALID_STATUS", message = "Knockout must be PAIRING")
            )
        }

        is GeneratePairingsResult.PairingsAlreadyExist -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "PAIRINGS_ALREADY_EXIST",
                    message = "Pairings already generated"
                )
            )
        }

        is GeneratePairingsResult.NotEnoughParticipants -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "NOT_ENOUGH_PARTICIPANTS",
                    message = "At least 2 participants required"
                )
            )
        }
    }
}

private fun Route.knockoutHeatRoute(
    jwtService: JwtService,
    knockoutService: KnockoutService,
    eventRepository: EventRepository,
) {
    post("/api/v1/events/{eventId}/knockout/heat") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val request = call.receive<CreateHeatForMatchRequestModel>()
        val matchId = UUID.fromString(request.matchId)
        val result = knockoutService.createHeatForMatch(
            eventId = eventId,
            matchId = matchId,
            actorId = principal.userId
        )
        call.respondCreateHeatResult(result)
    }
}

private suspend fun ApplicationCall.respondCreateHeatResult(result: CreateHeatForMatchResult) {
    when (result) {
        is CreateHeatForMatchResult.Success -> {
            respond(status = HttpStatusCode.Created, message = result.heat.toResponseModel())
        }

        is CreateHeatForMatchResult.TournamentNotFound -> {
            respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "KNOCKOUT_NOT_FOUND", message = "Knockout not found")
            )
        }

        is CreateHeatForMatchResult.MatchNotFound -> {
            respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "MATCH_NOT_FOUND", message = "Match not found")
            )
        }

        is CreateHeatForMatchResult.MatchAlreadyCompleted -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(code = "MATCH_ALREADY_COMPLETED", message = "Match already completed")
            )
        }

        is CreateHeatForMatchResult.MissingParticipants -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(code = "MISSING_PARTICIPANTS", message = "Match has no participants")
            )
        }
    }
}

private fun Route.knockoutResultRoute(
    jwtService: JwtService,
    knockoutService: KnockoutService,
    eventRepository: EventRepository,
) {
    post("/api/v1/events/{eventId}/knockout/result") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val request = call.receive<RecordMatchResultRequestModel>()
        val matchId = UUID.fromString(request.matchId)
        val winnerId = UUID.fromString(request.winnerId)
        val heatId = UUID.fromString(request.heatId)
        val result = knockoutService.recordMatchResult(
            eventId = eventId,
            matchId = matchId,
            winnerId = winnerId,
            heatId = heatId,
            actorId = principal.userId
        )
        call.respondRecordMatchResult(result)
    }
}

private suspend fun ApplicationCall.respondRecordMatchResult(result: RecordMatchResult) {
    when (result) {
        is RecordMatchResult.Success -> {
            respond(
                status = HttpStatusCode.OK,
                message = ErrorResponseModel(code = "OK", message = "Match result recorded")
            )
        }

        is RecordMatchResult.TournamentNotFound -> {
            respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "KNOCKOUT_NOT_FOUND", message = "Knockout not found")
            )
        }

        is RecordMatchResult.MatchNotFound -> {
            respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "MATCH_NOT_FOUND", message = "Match not found")
            )
        }

        is RecordMatchResult.MatchAlreadyCompleted -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(code = "MATCH_ALREADY_COMPLETED", message = "Match already completed")
            )
        }

        is RecordMatchResult.WinnerNotInMatch -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "WINNER_NOT_IN_MATCH",
                    message = "Winner must be a participant in the match"
                )
            )
        }
    }
}

private fun Route.knockoutFinalizeRoute(
    jwtService: JwtService,
    knockoutService: KnockoutService,
    eventRepository: EventRepository,
) {
    post("/api/v1/events/{eventId}/knockout/finalize") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post
        val result = knockoutService.finalize(eventId = eventId, actorId = principal.userId)
        call.respondFinalizeResult(result)
    }
}

private suspend fun ApplicationCall.respondFinalizeResult(result: FinalizeKnockoutResult) {
    when (result) {
        is FinalizeKnockoutResult.Success -> {
            respond(
                status = HttpStatusCode.OK,
                message = ErrorResponseModel(code = "OK", message = "Knockout finalized")
            )
        }

        is FinalizeKnockoutResult.TournamentNotFound -> {
            respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "KNOCKOUT_NOT_FOUND", message = "Knockout not found")
            )
        }

        is FinalizeKnockoutResult.InvalidStatus -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(code = "INVALID_STATUS", message = "Knockout must be IN_PROGRESS")
            )
        }

        is FinalizeKnockoutResult.IncompleteMatches -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "INCOMPLETE_MATCHES",
                    message = "${result.count} match(es) still incomplete"
                )
            )
        }
    }
}

private fun Route.knockoutMatchListRoutes(
    jwtService: JwtService,
    knockoutService: KnockoutService,
    eventRepository: EventRepository,
) {
    get("/api/v1/events/{eventId}/knockout/matches") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@get
        val matches = knockoutService.getMatches(eventId = eventId)
        call.respond(matches.map { it.toResponseModel() })
    }

    get("/api/v1/events/{eventId}/knockout/results") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@get
        val results = knockoutService.getResults(eventId = eventId)
        call.respond(results.map { it.toResponseModel() })
    }
}

private fun KnockoutTournamentEntity.toResponseModel(): KnockoutTournamentResponseModel {
    return KnockoutTournamentResponseModel(
        id = id.toString(),
        eventId = eventId.toString(),
        status = status.name,
        pairingMode = pairingMode.name,
        qualificationId = qualificationId.toString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt?.toString(),
        finalizedAt = finalizedAt?.toString(),
        finalizedBy = finalizedBy?.toString(),
    )
}

private fun KnockoutMatchEntity.toResponseModel(): KnockoutMatchResponseModel {
    return KnockoutMatchResponseModel(
        id = id.toString(),
        tournamentId = tournamentId.toString(),
        roundNumber = roundNumber,
        matchNumber = matchNumber,
        participant1Id = participant1Id?.toString(),
        participant2Id = participant2Id?.toString(),
        winnerId = winnerId?.toString(),
        heatId = heatId?.toString(),
        status = status.name,
        createdAt = createdAt.toString(),
    )
}

private fun KnockoutResultEntry.toResponseModel(): KnockoutResultEntryResponseModel {
    return KnockoutResultEntryResponseModel(
        rank = rank,
        participantId = participantId.toString(),
        firstName = firstName,
        lastName = lastName,
        startNumber = startNumber,
        club = club,
    )
}

private fun QualificationRanking.toQualifiedResponseModel(): QualifiedParticipantResponseModel {
    return QualifiedParticipantResponseModel(
        participantId = participantId.toString(),
        startNumber = startNumber,
        firstName = firstName,
        lastName = lastName,
        club = club,
        qualificationRank = rank,
    )
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
