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
import io.github.raginlundf.racingmanager.application.auth.AuthService
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
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Route.knockoutRoutes(authService: AuthService, knockoutService: KnockoutService) {
    post("/api/v1/events/{eventId}/knockout/setup") {
        val session = call.authenticateRequest(authService) ?: return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        val request = call.receive<SetupKnockoutRequestModel>()
        val pairingMode = try {
            PairingMode.valueOf(request.pairingMode)
        } catch (_: IllegalArgumentException) {
            call.respond(status = HttpStatusCode.BadRequest, message = ErrorResponseModel("INVALID_PAIRING_MODE", "Invalid pairing mode"))
            return@post
        }

        when (val result = knockoutService.setup(eventId, pairingMode, session.user.id)) {
            is SetupKnockoutResult.Success -> {
                call.respond(status = HttpStatusCode.Created, message = result.tournament.toResponseModel())
            }
            is SetupKnockoutResult.EventNotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("EVENT_NOT_FOUND", "Event not found"))
            }
            is SetupKnockoutResult.EventNotActive -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("EVENT_NOT_ACTIVE", "Event must be ACTIVE"))
            }
            is SetupKnockoutResult.AlreadyExists -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("KNOCKOUT_ALREADY_EXISTS", "Knockout already exists"))
            }
            is SetupKnockoutResult.QualificationNotFinalized -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("QUALIFICATION_NOT_FINALIZED", "Qualification must be finalized"))
            }
            is SetupKnockoutResult.NotEnoughParticipants -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("NOT_ENOUGH_PARTICIPANTS", "At least 2 participants required"))
            }
        }
    }

    get("/api/v1/events/{eventId}/knockout") {
        val session = call.authenticateRequest(authService) ?: return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        val tournament = knockoutService.findByEventId(eventId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel("KNOCKOUT_NOT_FOUND", "Knockout not found"),
            )
        call.respond(tournament.toResponseModel())
    }

    get("/api/v1/events/{eventId}/knockout/qualified-participants") {
        val session = call.authenticateRequest(authService) ?: return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        val participants = knockoutService.getQualifiedParticipants(eventId)
        call.respond(participants.map { it.toQualifiedResponseModel() })
    }

    post("/api/v1/events/{eventId}/knockout/manual-pairings") {
        val session = call.authenticateRequest(authService) ?: return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        val request = call.receive<SetManualPairingsRequestModel>()
        val pairings = request.pairings.map { p ->
            Pair(UUID.fromString(p.participant1Id), p.participant2Id?.let { UUID.fromString(it) })
        }

        when (val result = knockoutService.setManualPairings(eventId, pairings, session.user.id)) {
            is SetManualPairingsResult.Success -> {
                call.respond(result.tournament.toResponseModel())
            }
            is SetManualPairingsResult.TournamentNotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("KNOCKOUT_NOT_FOUND", "Knockout not found"))
            }
            is SetManualPairingsResult.InvalidStatus -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INVALID_STATUS", "Knockout must be PAIRING"))
            }
            is SetManualPairingsResult.PairingsAlreadyExist -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("PAIRINGS_ALREADY_EXIST", "Pairings already generated"))
            }
            is SetManualPairingsResult.NotEnoughParticipants -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("NOT_ENOUGH_PARTICIPANTS", "At least 1 pairing required"))
            }
            is SetManualPairingsResult.WrongPairingMode -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("WRONG_PAIRING_MODE", "Knockout must be in MANUAL mode"))
            }
        }
    }

    post("/api/v1/events/{eventId}/knockout/pairings") {
        val session = call.authenticateRequest(authService) ?: return@post
        val eventId = UUID.fromString(call.parameters["eventId"])

        when (val result = knockoutService.generatePairings(eventId, session.user.id)) {
            is GeneratePairingsResult.Success -> {
                call.respond(result.tournament.toResponseModel())
            }
            is GeneratePairingsResult.TournamentNotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("KNOCKOUT_NOT_FOUND", "Knockout not found"))
            }
            is GeneratePairingsResult.InvalidStatus -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INVALID_STATUS", "Knockout must be PAIRING"))
            }
            is GeneratePairingsResult.PairingsAlreadyExist -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("PAIRINGS_ALREADY_EXIST", "Pairings already generated"))
            }
            is GeneratePairingsResult.NotEnoughParticipants -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("NOT_ENOUGH_PARTICIPANTS", "At least 2 participants required"))
            }
        }
    }

    get("/api/v1/events/{eventId}/knockout/matches") {
        val session = call.authenticateRequest(authService) ?: return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        val matches = knockoutService.getMatches(eventId)
        call.respond(matches.map { it.toResponseModel() })
    }

    post("/api/v1/events/{eventId}/knockout/heat") {
        val session = call.authenticateRequest(authService) ?: return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        val request = call.receive<CreateHeatForMatchRequestModel>()
        val matchId = UUID.fromString(request.matchId)

        when (val result = knockoutService.createHeatForMatch(eventId, matchId, session.user.id)) {
            is CreateHeatForMatchResult.Success -> {
                call.respond(status = HttpStatusCode.Created, message = result.heat)
            }
            is CreateHeatForMatchResult.TournamentNotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("KNOCKOUT_NOT_FOUND", "Knockout not found"))
            }
            is CreateHeatForMatchResult.MatchNotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("MATCH_NOT_FOUND", "Match not found"))
            }
            is CreateHeatForMatchResult.MatchAlreadyCompleted -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("MATCH_ALREADY_COMPLETED", "Match already completed"))
            }
            is CreateHeatForMatchResult.MissingParticipants -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("MISSING_PARTICIPANTS", "Match has no participants"))
            }
        }
    }

    post("/api/v1/events/{eventId}/knockout/result") {
        val session = call.authenticateRequest(authService) ?: return@post
        val eventId = UUID.fromString(call.parameters["eventId"])
        val request = call.receive<RecordMatchResultRequestModel>()
        val matchId = UUID.fromString(request.matchId)
        val winnerId = UUID.fromString(request.winnerId)
        val heatId = UUID.fromString(request.heatId)

        when (val result = knockoutService.recordMatchResult(eventId, matchId, winnerId, heatId, session.user.id)) {
            is RecordMatchResult.Success -> {
                call.respond(status = HttpStatusCode.OK, message = ErrorResponseModel("OK", "Match result recorded"))
            }
            is RecordMatchResult.TournamentNotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("KNOCKOUT_NOT_FOUND", "Knockout not found"))
            }
            is RecordMatchResult.MatchNotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("MATCH_NOT_FOUND", "Match not found"))
            }
            is RecordMatchResult.MatchAlreadyCompleted -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("MATCH_ALREADY_COMPLETED", "Match already completed"))
            }
            is RecordMatchResult.WinnerNotInMatch -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("WINNER_NOT_IN_MATCH", "Winner must be a participant in the match"))
            }
        }
    }

    post("/api/v1/events/{eventId}/knockout/finalize") {
        val session = call.authenticateRequest(authService) ?: return@post
        val eventId = UUID.fromString(call.parameters["eventId"])

        when (val result = knockoutService.finalize(eventId, session.user.id)) {
            is FinalizeKnockoutResult.Success -> {
                call.respond(status = HttpStatusCode.OK, message = ErrorResponseModel("OK", "Knockout finalized"))
            }
            is FinalizeKnockoutResult.TournamentNotFound -> {
                call.respond(status = HttpStatusCode.NotFound, message = ErrorResponseModel("KNOCKOUT_NOT_FOUND", "Knockout not found"))
            }
            is FinalizeKnockoutResult.InvalidStatus -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INVALID_STATUS", "Knockout must be IN_PROGRESS"))
            }
            is FinalizeKnockoutResult.IncompleteMatches -> {
                call.respond(status = HttpStatusCode.Conflict, message = ErrorResponseModel("INCOMPLETE_MATCHES", "${result.count} match(es) still incomplete"))
            }
        }
    }

    get("/api/v1/events/{eventId}/knockout/results") {
        val session = call.authenticateRequest(authService) ?: return@get
        val eventId = UUID.fromString(call.parameters["eventId"])
        val results = knockoutService.getResults(eventId)
        call.respond(results.map { it.toResponseModel() })
    }
}

private fun KnockoutTournamentEntity.toResponseModel() = KnockoutTournamentResponseModel(
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

private fun KnockoutMatchEntity.toResponseModel() = KnockoutMatchResponseModel(
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

private fun KnockoutResultEntry.toResponseModel() = KnockoutResultEntryResponseModel(
    rank = rank,
    participantId = participantId.toString(),
    firstName = firstName,
    lastName = lastName,
    startNumber = startNumber,
    club = club,
)

private fun QualificationRanking.toQualifiedResponseModel() = QualifiedParticipantResponseModel(
    participantId = participantId.toString(),
    startNumber = startNumber,
    firstName = firstName,
    lastName = lastName,
    club = club,
    qualificationRank = rank,
)
