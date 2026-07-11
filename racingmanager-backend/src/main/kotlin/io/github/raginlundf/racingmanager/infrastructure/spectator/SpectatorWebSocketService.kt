package io.github.raginlundf.racingmanager.infrastructure.spectator

import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorEventModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorHeatModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorKnockoutMatchModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorKnockoutRoundModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorKnockoutStateModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorLaneModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorRankingEntryModel
import io.github.raginlundf.racingmanager.api.spectator.models.SpectatorSnapshotResponseModel
import io.github.raginlundf.racingmanager.application.heat.HeatServiceEvent
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SpectatorWebSocketService(
    private val spectatorService: SpectatorService,
    private val heatRepository: HeatRepository,
    private val heatServiceEvents: SharedFlow<HeatServiceEvent>,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val connections = ConcurrentHashMap<UUID, MutableSet<DefaultWebSocketServerSession>>()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private var listenerJob: Job? = null

    fun start() {
        listenerJob = scope.launch {
            heatServiceEvents.collect { event ->
                val eventId = when (event) {
                    is HeatServiceEvent.HeatCreated -> event.heat.eventId
                    is HeatServiceEvent.HeatStateChanged -> event.heat.eventId
                    is HeatServiceEvent.HeatResultAccepted -> heatRepository.findEventIdByHeatId(event.heatId)
                    is HeatServiceEvent.HeatResultRejected -> heatRepository.findEventIdByHeatId(event.heatId)
                }
                if (eventId != null) {
                    broadcastSnapshot(eventId)
                }
            }
        }
    }

    fun addConnection(eventId: UUID, session: DefaultWebSocketServerSession) {
        connections.computeIfAbsent(eventId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    fun removeConnection(eventId: UUID, session: DefaultWebSocketServerSession) {
        connections[eventId]?.remove(session)
        if (connections[eventId]?.isEmpty() == true) {
            connections.remove(eventId)
        }
    }

    private suspend fun broadcastSnapshot(eventId: UUID) {
        val snapshot = spectatorService.getSnapshot(eventId) ?: return
        val responseModel = SpectatorSnapshotResponseModel(
            event = SpectatorEventModel(
                id = snapshot.event.id.toString(),
                name = snapshot.event.name,
                description = snapshot.event.description,
                status = snapshot.event.status.name,
                laneType = snapshot.event.settings.laneType.name,
                measurementType = snapshot.event.settings.measurementType.name,
            ),
            currentHeat = snapshot.currentHeat?.let { h ->
                SpectatorHeatModel(
                    id = h.id.toString(),
                    heatNumber = h.heatNumber,
                    round = h.round,
                    status = h.status.name,
                    lanes = h.lanes.map { l ->
                        val measurement = h.measurements.firstOrNull { it.lane == l.lane }
                        SpectatorLaneModel(
                            lane = l.lane,
                            participantId = l.participantId.toString(),
                            participantStartNumber = l.participantStartNumber,
                            participantFirstName = l.participantFirstName,
                            participantLastName = l.participantLastName,
                            durationNanos = measurement?.durationNanos,
                            outcome = measurement?.outcome?.name,
                        )
                    },
                    hasResult = h.measurements.any { it.outcome.name == "FINISHED" || it.outcome.name == "DNF" },
                )
            },
            upcomingHeats = snapshot.upcomingHeats.map { h ->
                SpectatorHeatModel(
                    id = h.id.toString(),
                    heatNumber = h.heatNumber,
                    round = h.round,
                    status = h.status.name,
                    lanes = h.lanes.map { l ->
                        SpectatorLaneModel(
                            lane = l.lane,
                            participantId = l.participantId.toString(),
                            participantStartNumber = l.participantStartNumber,
                            participantFirstName = l.participantFirstName,
                            participantLastName = l.participantLastName,
                        )
                    },
                )
            },
            qualificationRankings = snapshot.qualificationRankings.map { r ->
                SpectatorRankingEntryModel(
                    participantId = r.participantId.toString(),
                    startNumber = r.startNumber,
                    firstName = r.firstName,
                    lastName = r.lastName,
                    club = r.club,
                    bestTimeNanos = r.bestTimeNanos,
                    totalTimeNanos = r.totalTimeNanos,
                    completedRuns = r.completedRuns,
                    dnfCount = r.dnfCount,
                    rank = r.rank,
                )
            },
            qualificationStatus = snapshot.qualificationStatus,
            knockout = snapshot.knockout?.let { k ->
                SpectatorKnockoutStateModel(
                    status = k.status,
                    pairingMode = k.pairingMode,
                    rounds = k.rounds.map { r ->
                        SpectatorKnockoutRoundModel(
                            roundNumber = r.roundNumber,
                            matches = r.matches.map { m ->
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
        val payload = json.encodeToString(responseModel)
        connections[eventId]?.toList()?.forEach { session ->
            if (session.isActive) {
                try {
                    session.send(payload)
                } catch (_: Exception) {
                    connections[eventId]?.remove(session)
                }
            } else {
                connections[eventId]?.remove(session)
            }
        }
    }
}
