package io.github.raginlundf.racingmanager.infrastructure.gateway

import io.github.raginlundf.racingmanager.application.heat.GatewayArmResult
import io.github.raginlundf.racingmanager.application.heat.GatewayCancelResult
import io.github.raginlundf.racingmanager.application.heat.MeasurementGateway
import io.github.raginlundf.racingmanager.application.heat.MeasurementGatewayEvent
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Random
import java.util.UUID

class SimulationMeasurementGateway(
    private val seed: Long = DEFAULT_SEED,
) : MeasurementGateway {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _events = MutableSharedFlow<MeasurementGatewayEvent>(extraBufferCapacity = 64)
    private val activeHeats = mutableSetOf<UUID>()

    override fun events() = _events.asSharedFlow()

    override suspend fun arm(heat: HeatEntity): GatewayArmResult {
        activeHeats.add(heat.id)
        return GatewayArmResult.Success
    }

    override suspend fun cancel(heatId: UUID): GatewayCancelResult {
        activeHeats.remove(heatId)
        return GatewayCancelResult.Success
    }

    fun simulateHeat(heat: HeatEntity) {
        scope.launch {
            if (heat.id !in activeHeats) return@launch

            _events.emit(MeasurementGatewayEvent.HeatStarted(heat.id))

            val random = Random(seed + heat.round.toLong() * 1000 + heat.heatNumber.toLong())
            val baseTimeNanos = 3_000_000_000L + random.nextLong(2_000_000_000)

            heat.lanes.forEach { lane ->
                val laneVariance = random.nextLong(500_000_000)
                val dnf = random.nextDouble() < 0.05
                val durationNanos = if (dnf) 0L else baseTimeNanos + laneVariance * lane.lane
                val outcome = if (dnf) LaneOutcome.DNF else LaneOutcome.FINISHED

                delay(1000 + random.nextLong(2000))

                if (heat.id !in activeHeats) return@launch

                _events.emit(
                    MeasurementGatewayEvent.LaneFinished(
                        heatId = heat.id,
                        lane = lane.lane,
                        durationNanos = durationNanos,
                        outcome = outcome,
                    ),
                )
            }

            delay(500)

            if (heat.id !in activeHeats) return@launch

            _events.emit(MeasurementGatewayEvent.HeatFinished(heat.id))
            activeHeats.remove(heat.id)
        }
    }

    companion object {
        const val DEFAULT_SEED = 42L
    }
}
