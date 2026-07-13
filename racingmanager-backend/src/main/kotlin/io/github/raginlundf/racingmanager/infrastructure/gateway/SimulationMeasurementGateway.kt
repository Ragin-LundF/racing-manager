package io.github.raginlundf.racingmanager.infrastructure.gateway

import io.github.raginlundf.racingmanager.application.heat.GatewayArmResult
import io.github.raginlundf.racingmanager.application.heat.GatewayCancelResult
import io.github.raginlundf.racingmanager.application.heat.MeasurementGateway
import io.github.raginlundf.racingmanager.application.heat.MeasurementGatewayEvent
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Random
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/** Simulates a race once a heat is started: after [rampDelayMs] (modelling
    the real ramp/light-barrier release), each lane independently finishes
    somewhere between [raceMinMs] and [raceMaxMs], or — with [dnfProbability]
    chance — is marked DNF after [dnfTimeoutMs]. Lane outcomes are emitted as
    they actually occur (not all at once), so a connected spectator view sees
    the race unfold in real time. */
class SimulationMeasurementGateway(
    private val seed: Long = DEFAULT_SEED,
    private val rampDelayMs: Long = DEFAULT_RAMP_DELAY_MS,
    private val raceMinMs: Long = DEFAULT_RACE_MIN_MS,
    private val raceMaxMs: Long = DEFAULT_RACE_MAX_MS,
    private val dnfTimeoutMs: Long = DEFAULT_DNF_TIMEOUT_MS,
    private val dnfProbability: Double = DEFAULT_DNF_PROBABILITY,
) : MeasurementGateway {
    private val scope = CoroutineScope(context = Dispatchers.Default)
    private val _events = MutableSharedFlow<MeasurementGatewayEvent>(extraBufferCapacity = 64)
    private val activeHeats = mutableSetOf<UUID>()

    override fun events(): kotlinx.coroutines.flow.Flow<MeasurementGatewayEvent> {
        return _events.asSharedFlow()
    }

    override suspend fun arm(heat: HeatEntity): GatewayArmResult {
        activeHeats.add(element = heat.id)
        return GatewayArmResult.Success
    }

    override suspend fun cancel(heatId: UUID): GatewayCancelResult {
        activeHeats.remove(element = heatId)
        return GatewayCancelResult.Success
    }

    /** The heat is already STARTED (with its real `startedAt`) by the time
        this is called — no [MeasurementGatewayEvent.HeatStarted] is emitted
        here, since that would incorrectly shift `startedAt` by [rampDelayMs]. */
    override fun simulateHeat(heat: HeatEntity) {
        if (heat.id !in activeHeats) return

        val random = Random(seed xor heat.id.hashCode().toLong())
        val laneOutcomes = heat.lanes.map { lane ->
            val dnf = random.nextDouble() < dnfProbability
            val durationMs = if (dnf) dnfTimeoutMs else raceMinMs + random.nextLong(raceMaxMs - raceMinMs + 1)
            Triple(first = lane.lane, second = dnf, third = durationMs)
        }

        scope.launch {
            delay(duration = rampDelayMs.milliseconds)
            if (heat.id !in activeHeats) return@launch

            val laneJobs = laneOutcomes.map { (lane, dnf, durationMs) ->
                async {
                    delay(duration = durationMs.milliseconds)
                    if (heat.id !in activeHeats) return@async
                    _events.emit(
                        value = MeasurementGatewayEvent.LaneFinished(
                            heatId = heat.id,
                            lane = lane,
                            durationNanos = if (dnf) 0L else durationMs * 1_000_000L,
                            outcome = if (dnf) LaneOutcome.DNF else LaneOutcome.FINISHED,
                        ),
                    )
                }
            }
            laneJobs.awaitAll()

            if (heat.id !in activeHeats) return@launch
            _events.emit(value = MeasurementGatewayEvent.HeatFinished(heatId = heat.id))
            activeHeats.remove(element = heat.id)
        }
    }

    companion object {
        const val DEFAULT_SEED = 42L
        const val DEFAULT_RAMP_DELAY_MS = 3_000L
        const val DEFAULT_RACE_MIN_MS = 4_000L
        const val DEFAULT_RACE_MAX_MS = 7_000L
        const val DEFAULT_DNF_TIMEOUT_MS = 10_000L
        const val DEFAULT_DNF_PROBABILITY = 0.10
    }
}
