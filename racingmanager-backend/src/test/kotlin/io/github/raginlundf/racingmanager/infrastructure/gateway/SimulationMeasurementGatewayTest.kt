package io.github.raginlundf.racingmanager.infrastructure.gateway

import io.github.raginlundf.racingmanager.application.heat.MeasurementGatewayEvent
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import java.util.UUID

class SimulationMeasurementGatewayTest {

    private fun heatWithLanes(laneCount: Int): HeatEntity {
        val now = Clock.System.now()
        return HeatEntity(
            id = UUID.randomUUID(),
            eventId = UUID.randomUUID(),
            round = 1,
            heatNumber = 1,
            status = HeatStatus.STARTED,
            lanes = (1..laneCount).map { lane ->
                HeatLaneAssignment(
                    lane = lane,
                    participantId = UUID.randomUUID(),
                    participantStartNumber = lane,
                    participantFirstName = "First$lane",
                    participantLastName = "Last$lane",
                )
            },
            measurements = emptyList(),
            createdAt = now,
            startedAt = now,
        )
    }

    /** Subscribes to [gateway]'s events, then triggers [heat]'s simulation,
        collecting until [MeasurementGatewayEvent.HeatFinished] or the
        timeout elapses (for the "never armed" case, which never finishes). */
    private suspend fun collectUntilFinished(
        gateway: SimulationMeasurementGateway,
        heat: HeatEntity,
        timeoutMs: Long = 2_000,
    ): List<MeasurementGatewayEvent> {
        val collected = mutableListOf<MeasurementGatewayEvent>()
        coroutineScope {
            val job = launch {
                gateway.events().collect { event ->
                    collected.add(event)
                    if (event is MeasurementGatewayEvent.HeatFinished) cancel()
                }
            }
            yield()
            gateway.simulateHeat(heat)
            try {
                withTimeout(timeoutMs) { job.join() }
            } catch (_: Exception) {
                job.cancel()
            }
        }
        return collected
    }

    @Test
    fun `simulateHeat emits one LaneFinished per lane then HeatFinished, no HeatStarted`() = runBlocking {
        val gateway = SimulationMeasurementGateway(
            rampDelayMs = 2,
            raceMinMs = 2,
            raceMaxMs = 4,
            dnfTimeoutMs = 6,
            dnfProbability = 0.0,
        )
        val heat = heatWithLanes(3)
        gateway.arm(heat)

        val events = collectUntilFinished(gateway, heat)

        assertFalse(events.any { it is MeasurementGatewayEvent.HeatStarted })
        val laneFinished = events.filterIsInstance<MeasurementGatewayEvent.LaneFinished>()
        assertEquals(3, laneFinished.size)
        assertEquals(setOf(1, 2, 3), laneFinished.map { it.lane }.toSet())
        assertTrue(laneFinished.all { it.outcome == LaneOutcome.FINISHED })
        assertTrue(laneFinished.all { it.durationNanos in 2_000_000L..4_000_000L })
        assertTrue(events.last() is MeasurementGatewayEvent.HeatFinished)
    }

    @Test
    fun `simulateHeat with certain DNF marks every lane DNF with zero duration`() = runBlocking {
        val gateway = SimulationMeasurementGateway(
            rampDelayMs = 1,
            raceMinMs = 1,
            raceMaxMs = 2,
            dnfTimeoutMs = 3,
            dnfProbability = 1.0,
        )
        val heat = heatWithLanes(2)
        gateway.arm(heat)

        val laneFinished = collectUntilFinished(gateway, heat)
            .filterIsInstance<MeasurementGatewayEvent.LaneFinished>()

        assertEquals(2, laneFinished.size)
        assertTrue(laneFinished.all { it.outcome == LaneOutcome.DNF })
        assertTrue(laneFinished.all { it.durationNanos == 0L })
    }

    @Test
    fun `simulateHeat does nothing for a heat that was never armed`() = runBlocking {
        val gateway = SimulationMeasurementGateway(rampDelayMs = 1, raceMinMs = 1, raceMaxMs = 2)
        val heat = heatWithLanes(1)
        // never armed, so heat.id is not in activeHeats

        val events = collectUntilFinished(gateway, heat, timeoutMs = 100)

        assertTrue(events.isEmpty())
    }
}
