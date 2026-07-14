package io.github.raginlundf.racingmanager.infrastructure.gateway.simulator

import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.DeviceCommand
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.DeviceErrorCode
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.DeviceEvent
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.MessageCodec
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FakeRaspberryPiControllerTest {

    private val raceId = "race-1"

    private fun prepare(lanes: List<Int>): String {
        return MessageCodec.encodeCommand(
            raceId = raceId,
            command = DeviceCommand.PrepareRace(lanes = lanes, finishTimeoutMs = 30_000),
        )
    }

    private fun start(): String {
        return MessageCodec.encodeCommand(raceId = raceId, command = DeviceCommand.StartRace)
    }

    /** Subscribes to the controller's frames, runs [issue], and collects decoded
    events until [stopOn] matches or the timeout elapses. */
    private suspend fun collect(
        controller: FakeRaspberryPiController,
        stopOn: (DeviceEvent) -> Boolean,
        timeoutMs: Long = 2_000,
        issue: suspend () -> Unit,
    ): List<DeviceEvent> {
        val collected = mutableListOf<DeviceEvent>()
        coroutineScope {
            val job = launch {
                controller.outgoing().collect { frame ->
                    val event = MessageCodec.decodeEvent(text = frame).event
                    collected.add(element = event)
                    if (stopOn(event)) cancel()
                }
            }
            yield()
            issue()
            runCatching { withTimeout(timeMillis = timeoutMs) { job.join() } }.onFailure { job.cancel() }
        }
        return collected
    }

    @Test
    fun `prepareRace responds with raceReady for the requested lanes`() = runBlocking {
        val controller = FakeRaspberryPiController(rampDelayMs = 2, raceMinMs = 2, raceMaxMs = 4, dnfProbability = 0.0)

        val events = collect(controller = controller, stopOn = { it is DeviceEvent.RaceReady }) {
            controller.onCommand(text = prepare(lanes = listOf(1, 2)))
        }

        val ready = events.filterIsInstance<DeviceEvent.RaceReady>().single()
        assertEquals(expected = listOf(1, 2), actual = ready.lanes)
        assertEquals(expected = "closed", actual = ready.gateState)
    }

    @Test
    fun `startRace emits one finishDetected per lane then raceFinished`() = runBlocking {
        val controller = FakeRaspberryPiController(rampDelayMs = 2, raceMinMs = 2, raceMaxMs = 4, dnfProbability = 0.0)

        val events = collect(controller = controller, stopOn = { it is DeviceEvent.RaceFinished }) {
            controller.onCommand(text = prepare(lanes = listOf(1, 2)))
            controller.onCommand(text = start())
        }

        assertTrue(actual = events.any { it is DeviceEvent.RaceStarted })
        val finishes = events.filterIsInstance<DeviceEvent.FinishDetected>()
        assertEquals(expected = setOf(1, 2), actual = finishes.map { it.lane }.toSet())
        assertTrue(actual = finishes.all { it.elapsedNs in 2_000_000L..4_000_000L })
        val finished = events.filterIsInstance<DeviceEvent.RaceFinished>().single()
        assertTrue(actual = finished.results.all { it.status == "finished" })
    }

    @Test
    fun `repeating the same race produces fresh results`() = runBlocking {
        // Same raceId run twice (re-prepare → re-start). The attempt counter must vary the RNG so a
        // repeated race is not a byte-identical replay. Range is kept small for speed but wide enough
        // that two independent runs colliding on every lane is negligible.
        val controller = FakeRaspberryPiController(
            rampDelayMs = 1, raceMinMs = 2, raceMaxMs = 200, dnfProbability = 0.0,
        )

        suspend fun runOnce(): List<Long> {
            val events = collect(controller = controller, stopOn = { it is DeviceEvent.RaceFinished }) {
                controller.onCommand(text = prepare(lanes = listOf(1, 2)))
                controller.onCommand(text = start())
            }
            return events.filterIsInstance<DeviceEvent.FinishDetected>().sortedBy { it.lane }.map { it.elapsedNs }
        }

        val first = runOnce()
        val second = runOnce()

        assertEquals(expected = 2, actual = first.size)
        assertNotEquals(illegal = first, actual = second)
    }

    @Test
    fun `a certain-DNF race reports every lane as timeout with no finishDetected`() = runBlocking {
        val controller = FakeRaspberryPiController(
            rampDelayMs = 1,
            raceMinMs = 1,
            raceMaxMs = 2,
            dnfTimeoutMs = 3,
            dnfProbability = 1.0
        )

        val events = collect(controller = controller, stopOn = { it is DeviceEvent.RaceFinished }) {
            controller.onCommand(text = prepare(lanes = listOf(1, 2)))
            controller.onCommand(text = start())
        }

        assertFalse(actual = events.any { it is DeviceEvent.FinishDetected })
        val finished = events.filterIsInstance<DeviceEvent.RaceFinished>().single()
        assertTrue(actual = finished.results.all { it.status == "timeout" })
    }

    @Test
    fun `startRace without prepareRace is rejected as INVALID_STATE`() = runBlocking {
        val controller = FakeRaspberryPiController(rampDelayMs = 2)

        val events = collect(controller = controller, stopOn = { it is DeviceEvent.DeviceError }) {
            controller.onCommand(text = start())
        }

        val error = events.filterIsInstance<DeviceEvent.DeviceError>().single()
        assertEquals(expected = DeviceErrorCode.INVALID_STATE, actual = error.code)
    }

    @Test
    fun `a second startRace is rejected as DUPLICATE_COMMAND`() = runBlocking {
        // Large ramp delay keeps the race running so the duplicate arrives first.
        val controller = FakeRaspberryPiController(rampDelayMs = 500, raceMinMs = 500, raceMaxMs = 600)

        val events = collect(controller = controller, stopOn = { it is DeviceEvent.DeviceError }) {
            controller.onCommand(text = prepare(lanes = listOf(1, 2)))
            controller.onCommand(text = start())
            controller.onCommand(text = start())
        }

        val error = events.filterIsInstance<DeviceEvent.DeviceError>().single()
        assertEquals(expected = DeviceErrorCode.DUPLICATE_COMMAND, actual = error.code)
    }

    @Test
    fun `abortRace before the ramp delay stops the race with no finishes`() = runBlocking {
        val controller = FakeRaspberryPiController(rampDelayMs = 50, raceMinMs = 50, raceMaxMs = 60)

        val events = collect(controller = controller, stopOn = { it is DeviceEvent.RaceFinished }, timeoutMs = 400) {
            controller.onCommand(text = prepare(lanes = listOf(1, 2)))
            controller.onCommand(text = start())
            controller.onCommand(text = MessageCodec.encodeCommand(raceId = raceId, command = DeviceCommand.AbortRace))
        }

        assertFalse(actual = events.any { it is DeviceEvent.RaceFinished })
        assertFalse(actual = events.any { it is DeviceEvent.FinishDetected })
    }
}
