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
import kotlin.test.assertTrue
import kotlin.time.Clock
import java.util.UUID

class RaspberryPiMeasurementGatewayTest {

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

    @Test
    fun `arm then start yields one LaneFinished per lane and a HeatFinished`() = runBlocking {
        val gateway = RaspberryPiMeasurementGateway.simulated(
            rampDelayMs = 2,
            raceMinMs = 2,
            raceMaxMs = 4,
            dnfTimeoutMs = 6,
            dnfProbability = 0.0,
        )
        val heat = heatWithLanes(laneCount = 3)

        val collected = mutableListOf<MeasurementGatewayEvent>()
        coroutineScope {
            val job = launch {
                gateway.events().collect { event ->
                    collected.add(element = event)
                    if (event is MeasurementGatewayEvent.HeatFinished) cancel()
                }
            }
            yield()
            gateway.arm(heat = heat)
            gateway.start(heat = heat)
            runCatching { withTimeout(timeMillis = 2_000) { job.join() } }.onFailure { job.cancel() }
        }

        val laneFinished = collected.filterIsInstance<MeasurementGatewayEvent.LaneFinished>()
        assertEquals(expected = setOf(1, 2, 3), actual = laneFinished.map { it.lane }.toSet())
        assertTrue(laneFinished.all { it.outcome == LaneOutcome.FINISHED })
        assertTrue(laneFinished.all { it.durationNanos in 2_000_000L..4_000_000L })
        assertTrue(collected.last() is MeasurementGatewayEvent.HeatFinished)
    }

    @Test
    fun `a certain-DNF race maps every lane to a DNF LaneFinished`() = runBlocking {
        val gateway = RaspberryPiMeasurementGateway.simulated(
            rampDelayMs = 1,
            raceMinMs = 1,
            raceMaxMs = 2,
            dnfTimeoutMs = 3,
            dnfProbability = 1.0,
        )
        val heat = heatWithLanes(laneCount = 2)

        val collected = mutableListOf<MeasurementGatewayEvent>()
        coroutineScope {
            val job = launch {
                gateway.events().collect { event ->
                    collected.add(element = event)
                    if (event is MeasurementGatewayEvent.HeatFinished) cancel()
                }
            }
            yield()
            gateway.arm(heat = heat)
            gateway.start(heat = heat)
            runCatching { withTimeout(timeMillis = 2_000) { job.join() } }.onFailure { job.cancel() }
        }

        val laneFinished = collected.filterIsInstance<MeasurementGatewayEvent.LaneFinished>()
        assertEquals(expected = 2, actual = laneFinished.size)
        assertTrue(laneFinished.all { it.outcome == LaneOutcome.DNF })
        assertTrue(laneFinished.all { it.durationNanos == 0L })
    }
}
