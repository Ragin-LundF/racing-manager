package io.github.raginlundf.racingmanager.infrastructure.gateway

import io.github.raginlundf.racingmanager.application.heat.CloseableMeasurementGateway
import io.github.raginlundf.racingmanager.application.heat.GatewayArmResult
import io.github.raginlundf.racingmanager.application.heat.GatewayCancelResult
import io.github.raginlundf.racingmanager.application.heat.MeasurementGatewayEvent
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.ArduinoTwoLaneSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

    @Test
    fun `reconfigure closes the delegate it replaces`() = runBlocking {
        val replaced = RecordingGateway()
        val replacement = RecordingGateway()
        val delegates = ArrayDeque(listOf(replaced, replacement))
        val gateway = ReconfigurableMeasurementGateway(
            initialSettings = RaceDeviceSettings(
                mode = RaceDeviceMode.SIMULATED,
                endpoint = "ws://a",
                finishTimeoutMs = 30_000,
            ),
            buildDelegate = { delegates.removeFirst() },
        )

        gateway.reconfigure(
            newSettings = RaceDeviceSettings(
                mode = RaceDeviceMode.ARDUINO_TWO_LANE,
                endpoint = "ws://a",
                finishTimeoutMs = 30_000,
                arduino = ArduinoTwoLaneSettings(portName = "/dev/ttyACM0"),
            ),
        )

        assertTrue(actual = replaced.closed, message = "the replaced delegate must release its device connection")
        assertFalse(actual = replacement.closed)
    }

    /** A delegate that only records whether it was torn down — a real device
        connection is not needed to observe the swap. */
    private class RecordingGateway : CloseableMeasurementGateway {
        var closed = false
            private set

        override suspend fun arm(heat: HeatEntity): GatewayArmResult {
            return GatewayArmResult.Success
        }

        override suspend fun start(heat: HeatEntity) = Unit

        override suspend fun cancel(heatId: UUID): GatewayCancelResult {
            return GatewayCancelResult.Success
        }

        override fun events(): Flow<MeasurementGatewayEvent> {
            return emptyFlow()
        }

        override suspend fun close() {
            closed = true
        }
    }
}
