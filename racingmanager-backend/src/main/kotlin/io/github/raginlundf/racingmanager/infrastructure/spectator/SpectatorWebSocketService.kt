package io.github.raginlundf.racingmanager.infrastructure.spectator

import io.github.raginlundf.racingmanager.api.spectator.toResponseModel
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
        val payload = json.encodeToString(snapshot.toResponseModel())
        connections[eventId]?.toList()?.forEach { session ->
            if (session.isActive) {
                runCatching { session.send(payload) }
                    .onFailure { connections[eventId]?.remove(session) }
            } else {
                connections[eventId]?.remove(session)
            }
        }
    }
}
