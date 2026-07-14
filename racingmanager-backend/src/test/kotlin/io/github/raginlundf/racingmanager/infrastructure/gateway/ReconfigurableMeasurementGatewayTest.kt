package io.github.raginlundf.racingmanager.infrastructure.gateway

import io.github.raginlundf.racingmanager.application.heat.MeasurementGatewayEvent
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import java.util.UUID

class ReconfigurableMeasurementGatewayTest {

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

    private fun fastSimulator(): RaspberryPiMeasurementGateway {
        return RaspberryPiMeasurementGateway.simulated(
            rampDelayMs = 1,
            raceMinMs = 1,
            raceMaxMs = 2,
            dnfTimeoutMs = 4,
            dnfProbability = 0.0,
        )
    }

    @Test
    fun `current reflects the initial settings`() {
        val initial = RaceDeviceSettings(mode = RaceDeviceMode.SIMULATED, endpoint = "ws://a", finishTimeoutMs = 30_000)
        val gateway = ReconfigurableMeasurementGateway(initialSettings = initial, buildDelegate = { fastSimulator() })

        assertEquals(expected = initial, actual = gateway.current())
    }

    @Test
    fun `reconfigure builds a new delegate, updates current, and keeps the events stream flowing`() = runBlocking {
        var builds = 0
        val gateway = ReconfigurableMeasurementGateway(
            initialSettings = RaceDeviceSettings(
                mode = RaceDeviceMode.SIMULATED,
                endpoint = "ws://a",
                finishTimeoutMs = 30_000
            ),
            buildDelegate = {
                builds++
                fastSimulator()
            },
        )

        // A collector subscribed to the stream BEFORE any reconfigure must keep
        // receiving after the underlying delegate is swapped.
        val eventsBeforeReconfigure = gateway.events()
        val finishes = Channel<Unit>(capacity = 16)
        coroutineScope {
            val collector = launch {
                eventsBeforeReconfigure.collect { event ->
                    if (event is MeasurementGatewayEvent.HeatFinished) finishes.send(element = Unit)
                }
            }
            yield()

            // Race on the initial delegate.
            val firstHeat = heatWithLanes(laneCount = 2)
            gateway.arm(heat = firstHeat)
            gateway.start(heat = firstHeat)
            withTimeout(timeMillis = 2_000) { finishes.receive() }

            val updated = RaceDeviceSettings(
                mode = RaceDeviceMode.SIMULATED,
                endpoint = "ws://b",
                finishTimeoutMs = 10_000
            )
            gateway.reconfigure(newSettings = updated)

            assertEquals(expected = updated, actual = gateway.current())
            assertEquals(expected = 2, actual = builds) // initial build + reconfigure build

            // Race on the NEW delegate; the pre-existing collector still receives it.
            val secondHeat = heatWithLanes(laneCount = 2)
            gateway.arm(heat = secondHeat)
            gateway.start(heat = secondHeat)
            withTimeout(timeMillis = 2_000) { finishes.receive() }

            collector.cancel()
        }
    }
}
