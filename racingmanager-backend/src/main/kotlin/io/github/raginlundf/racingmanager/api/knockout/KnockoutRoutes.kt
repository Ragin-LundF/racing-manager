package io.github.raginlundf.racingmanager.api.knockout

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
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
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchEntity
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutTournamentEntity
import io.github.raginlundf.racingmanager.domain.knockout.PairingMode
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Route.knockoutRoutes(jwtService: JwtService, knockoutService: KnockoutService, eventRepository: EventRepository) {
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

        when (val result = knockoutService.setup(
            eventId = eventId,
            pairingMode = pairingMode,
            actorId = principal.userId
        )) {
            is SetupKnockoutResult.Success -> {
                call.respond(status = HttpStatusCode.Created, message = result.tournament.toResponseModel())
            }

            is SetupKnockoutResult.EventNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "EVENT_NOT_FOUND", message = "Event not found")
                )
            }

            is SetupKnockoutResult.EventNotActive -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(code = "EVENT_NOT_ACTIVE", message = "Event must be ACTIVE")
                )
            }

            is SetupKnockoutResult.AlreadyExists -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(code = "KNOCKOUT_ALREADY_EXISTS", message = "Knockout already exists")
                )
            }

            is SetupKnockoutResult.QualificationNotFinalized -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "QUALIFICATION_NOT_FINALIZED",
                        message = "Qualification must be finalized"
                    )
                )
            }

            is SetupKnockoutResult.NotEnoughParticipants -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "NOT_ENOUGH_PARTICIPANTS",
                        message = "At least 2 participants required"
                    )
                )
            }
        }
    }

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

        when (val result = knockoutService.setManualPairings(
            eventId = eventId,
            pairings = pairings,
            actorId = principal.userId
        )) {
            is SetManualPairingsResult.Success -> {
                call.respond(message = result.tournament.toResponseModel())
            }

            is SetManualPairingsResult.TournamentNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "KNOCKOUT_NOT_FOUND", message = "Knockout not found")
                )
            }

            is SetManualPairingsResult.InvalidStatus -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(code = "INVALID_STATUS", message = "Knockout must be PAIRING")
                )
            }

            is SetManualPairingsResult.PairingsAlreadyExist -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "PAIRINGS_ALREADY_EXIST",
                        message = "Pairings already generated"
                    )
                )
            }

            is SetManualPairingsResult.NotEnoughParticipants -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "NOT_ENOUGH_PARTICIPANTS",
                        message = "At least 1 pairing required"
                    )
                )
            }

            is SetManualPairingsResult.WrongPairingMode -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "WRONG_PAIRING_MODE",
                        message = "Knockout must be in MANUAL mode"
                    )
                )
            }
        }
    }

    post("/api/v1/events/{eventId}/knockout/pairings") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post

        when (val result = knockoutService.generatePairings(eventId = eventId, actorId = principal.userId)) {
            is GeneratePairingsResult.Success -> {
                call.respond(message = result.tournament.toResponseModel())
            }

            is GeneratePairingsResult.TournamentNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "KNOCKOUT_NOT_FOUND", message = "Knockout not found")
                )
            }

            is GeneratePairingsResult.InvalidStatus -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(code = "INVALID_STATUS", message = "Knockout must be PAIRING")
                )
            }

            is GeneratePairingsResult.PairingsAlreadyExist -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "PAIRINGS_ALREADY_EXIST",
                        message = "Pairings already generated"
                    )
                )
            }

            is GeneratePairingsResult.NotEnoughParticipants -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "NOT_ENOUGH_PARTICIPANTS",
                        message = "At least 2 participants required"
                    )
                )
            }
        }
    }

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

        when (val result = knockoutService.createHeatForMatch(
            eventId = eventId,
            matchId = matchId,
            actorId = principal.userId
        )) {
            is CreateHeatForMatchResult.Success -> {
                call.respond(status = HttpStatusCode.Created, message = result.heat)
            }

            is CreateHeatForMatchResult.TournamentNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "KNOCKOUT_NOT_FOUND", message = "Knockout not found")
                )
            }

            is CreateHeatForMatchResult.MatchNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "MATCH_NOT_FOUND", message = "Match not found")
                )
            }

            is CreateHeatForMatchResult.MatchAlreadyCompleted -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(code = "MATCH_ALREADY_COMPLETED", message = "Match already completed")
                )
            }

            is CreateHeatForMatchResult.MissingParticipants -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(code = "MISSING_PARTICIPANTS", message = "Match has no participants")
                )
            }
        }
    }

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

        when (knockoutService.recordMatchResult(
            eventId = eventId,
            matchId = matchId,
            winnerId = winnerId,
            heatId = heatId,
            actorId = principal.userId
        )) {
            is RecordMatchResult.Success -> {
                call.respond(
                    status = HttpStatusCode.OK,
                    message = ErrorResponseModel(code = "OK", message = "Match result recorded")
                )
            }

            is RecordMatchResult.TournamentNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "KNOCKOUT_NOT_FOUND", message = "Knockout not found")
                )
            }

            is RecordMatchResult.MatchNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "MATCH_NOT_FOUND", message = "Match not found")
                )
            }

            is RecordMatchResult.MatchAlreadyCompleted -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(code = "MATCH_ALREADY_COMPLETED", message = "Match already completed")
                )
            }

            is RecordMatchResult.WinnerNotInMatch -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "WINNER_NOT_IN_MATCH",
                        message = "Winner must be a participant in the match"
                    )
                )
            }
        }
    }

    post("/api/v1/events/{eventId}/knockout/finalize") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN, Scopes.USER)) return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        call.requireTenantEvent(
            principal = principal,
            eventId = eventId,
            eventRepository = eventRepository
        ) ?: return@post

        when (val result = knockoutService.finalize(eventId = eventId, actorId = principal.userId)) {
            is FinalizeKnockoutResult.Success -> {
                call.respond(
                    status = HttpStatusCode.OK,
                    message = ErrorResponseModel(code = "OK", message = "Knockout finalized")
                )
            }

            is FinalizeKnockoutResult.TournamentNotFound -> {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ErrorResponseModel(code = "KNOCKOUT_NOT_FOUND", message = "Knockout not found")
                )
            }

            is FinalizeKnockoutResult.InvalidStatus -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(code = "INVALID_STATUS", message = "Knockout must be IN_PROGRESS")
                )
            }

            is FinalizeKnockoutResult.IncompleteMatches -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "INCOMPLETE_MATCHES",
                        message = "${result.count} match(es) still incomplete"
                    )
                )
            }
        }
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
