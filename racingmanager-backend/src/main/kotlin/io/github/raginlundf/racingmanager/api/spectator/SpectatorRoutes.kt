package io.github.raginlundf.racingmanager.api.spectator

import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorEventListItemModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorEventListResponseModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorEventModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorHeatModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorKnockoutMatchModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorKnockoutRoundModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorKnockoutStateModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorLaneModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorRankingEntryModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorSnapshotResponseModel
import io.github.raginlundf.racingmanager.application.spectator.SpectatorKnockoutMatch
import io.github.raginlundf.racingmanager.application.spectator.SpectatorKnockoutRound
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
import io.github.raginlundf.racingmanager.application.spectator.SpectatorSnapshot
import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.domain.heat.Measurement
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.spectator.SpectatorWebSocketService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.websocket.webSocket
import java.util.UUID

fun Route.spectatorRoutes(spectatorService: SpectatorService, eventRepository: EventRepository, webSocketService: SpectatorWebSocketService) {
    get("/api/v1/public/events") {
        val events = eventRepository.findAll().filter { it.status in listOf(EventStatus.ACTIVE, EventStatus.ARCHIVED) }
        call.respond(
            SpectatorEventListResponseModel(
                events = events.map { e ->
                    SpectatorEventListItemModel(
                        id = e.id.toString(),
                        name = e.name,
                        status = e.status.name,
                    )
                },
            ),
        )
    }

    webSocket("/api/v1/public/events/{eventId}/live") {
        val eventId = UUID.fromString(call.parameters["eventId"])
        webSocketService.addConnection(eventId = eventId, session = this)
        runCatching {
            for (frame in incoming) {}
        }.also {
            webSocketService.removeConnection(eventId = eventId, session = this)
        }
    }

    get("/api/v1/public/events/{eventId}/snapshot") {
        val eventId = UUID.fromString(call.parameters["eventId"])
        val snapshot = spectatorService.getSnapshot(eventId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = mapOf("error" to "Event not found"),
            )
        call.respond(snapshot.toResponseModel())
    }
}

internal fun SpectatorSnapshot.toResponseModel() = SpectatorSnapshotResponseModel(
    event = SpectatorEventModel(
        id = event.id.toString(),
        name = event.name,
        description = event.description,
        status = event.status.name,
        laneType = event.settings.laneType.name,
        measurementType = event.settings.measurementType.name,
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
)

private fun HeatEntity.toResponseModel(): SpectatorHeatModel {
    val finishedMeasurements = measurements.filter { it.outcome == LaneOutcome.FINISHED || it.outcome == LaneOutcome.DNF }
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

private fun QualificationRanking.toResponseModel() = SpectatorRankingEntryModel(
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
