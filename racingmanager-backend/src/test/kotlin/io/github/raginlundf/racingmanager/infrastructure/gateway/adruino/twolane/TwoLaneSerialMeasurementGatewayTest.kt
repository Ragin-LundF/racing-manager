package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

import io.github.raginlundf.racingmanager.application.heat.GatewayArmResult
import io.github.raginlundf.racingmanager.application.heat.GatewayCancelResult
import io.github.raginlundf.racingmanager.application.heat.MeasurementGatewayEvent
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.nio.file.Files
import java.util.UUID
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

private const val NANOS_PER_MILLI = 1_000_000L

class TwoLaneSerialMeasurementGatewayTest {

    private val harnesses = mutableListOf<Harness>()

    @AfterTest
    fun tearDown() {
        runBlocking { harnesses.forEach { it.close() } }
    }

    @Test
    fun `arm locks both lanes once the ready banner arrived`() = runBlocking {
        val harness = harness()
        harness.port.pushReadyBanner()

        val result = harness.gateway.arm(heat = heatWithLanes(laneCount = 2))

        assertEquals(expected = GatewayArmResult.Success, actual = result)
        assertEquals(expected = listOf("CMD;A;LOCK", "CMD;B;LOCK"), actual = harness.port.written.toList())
    }

    @Test
    fun `arm fails when no ready banner arrives within the timeout`() = runBlocking {
        val harness = harness(readyTimeoutMs = 50)

        val result = harness.gateway.arm(heat = heatWithLanes(laneCount = 2))

        assertIs<GatewayArmResult.Error>(value = result)
        assertContains(charSequence = result.message, other = "ready banner")
    }

    @Test
    fun `arm fails when the serial port cannot be opened`() = runBlocking {
        val harness = harness(openFailure = "Cannot open serial port 'COM9'")

        val result = harness.gateway.arm(heat = heatWithLanes(laneCount = 2))

        assertIs<GatewayArmResult.Error>(value = result)
        assertContains(charSequence = result.message, other = "COM9")
    }

    @Test
    fun `arm rejects a heat with more lanes than the device has`() = runBlocking {
        val harness = harness()
        harness.port.pushReadyBanner()

        val result = harness.gateway.arm(heat = heatWithLanes(laneCount = 3))

        assertIs<GatewayArmResult.Error>(value = result)
        assertTrue(actual = harness.port.written.isEmpty())
    }

    @Test
    fun `arm rejects a second heat while one is still running`() = runBlocking {
        val harness = harness()
        harness.port.pushReadyBanner()
        harness.gateway.arm(heat = heatWithLanes(laneCount = 2))

        val result = harness.gateway.arm(heat = heatWithLanes(laneCount = 2))

        assertIs<GatewayArmResult.Error>(value = result)
    }

    @Test
    fun `start arms both lanes and reports the heat as started`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat()

        harness.gateway.start(heat = heat)

        assertEquals(
            expected = MeasurementGatewayEvent.HeatStarted(heatId = heat.id),
            actual = harness.nextEvent(),
        )
        assertEquals(
            expected = listOf("CMD;A;LOCK", "CMD;B;LOCK", "CMD;A;ARM", "CMD;B;ARM"),
            actual = harness.port.written.toList(),
        )
    }

    @Test
    fun `a lane time is the difference of the board's own start and finish timestamps`() = runBlocking {
        val harness = harness(finishSemantics = FinishSemantics.TIMESTAMP)
        val heat = harness.armedHeat()
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.port.push(line = "A;START;1000")
        harness.port.push(line = "B;START;1200")
        harness.port.push(line = "A;FINISH;4500")
        harness.port.push(line = "B;FINISH;5000")

        val laneA = harness.nextEvent()
        assertEquals(
            expected = MeasurementGatewayEvent.LaneFinished(
                heatId = heat.id,
                lane = 1,
                durationNanos = 3_500 * NANOS_PER_MILLI,
                outcome = LaneOutcome.FINISHED,
            ),
            actual = laneA,
        )
        val laneB = harness.nextEvent()
        assertEquals(
            expected = MeasurementGatewayEvent.LaneFinished(
                heatId = heat.id,
                lane = 2,
                durationNanos = 3_800 * NANOS_PER_MILLI,
                outcome = LaneOutcome.FINISHED,
            ),
            actual = laneB,
        )
        assertEquals(expected = MeasurementGatewayEvent.HeatFinished(heatId = heat.id), actual = harness.nextEvent())
    }

    @Test
    fun `ELAPSED semantics read the finish value as the duration itself`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.port.push(line = "A;START;1000")
        harness.port.push(line = "A;FINISH;4500")

        val finished = assertIs<MeasurementGatewayEvent.LaneFinished>(value = harness.nextEvent())
        assertEquals(expected = 4_500 * NANOS_PER_MILLI, actual = finished.durationNanos)
    }

    @Test
    fun `a millis rollover between start and finish still yields the real duration`() = runBlocking {
        val harness = harness(finishSemantics = FinishSemantics.TIMESTAMP)
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        // 0xFFFFFFFF is reached 1000 ms after the start; the finish is 501 ms later.
        harness.port.push(line = "A;START;4294966295")
        harness.port.push(line = "A;FINISH;500")

        val finished = assertIs<MeasurementGatewayEvent.LaneFinished>(value = harness.nextEvent())
        assertEquals(expected = 1_501 * NANOS_PER_MILLI, actual = finished.durationNanos)
    }

    @Test
    fun `a trigger inside the false-start window fails the heat instead of timing it`() = runBlocking {
        val harness = harness(falseStartWindowMs = 10_000)
        val heat = harness.armedHeat()
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.port.push(line = "A;START;3565")

        val error = assertIs<MeasurementGatewayEvent.Error>(value = harness.nextEvent())
        assertEquals(expected = heat.id, actual = error.heatId)
        assertContains(charSequence = error.message, other = "false start or sensor fault")
        // No measurement may follow a fault: the finish is dropped with the heat.
        harness.port.push(line = "A;FINISH;9000")
        assertNull(actual = harness.nextEventOrNull())
    }

    @Test
    fun `a trigger exactly at the window boundary is a valid measurement`() = runBlocking {
        val harness = harness(falseStartWindowMs = 0)
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.port.push(line = "A;START;3565")
        harness.port.push(line = "A;FINISH;2000")

        val finished = assertIs<MeasurementGatewayEvent.LaneFinished>(value = harness.nextEvent())
        assertEquals(expected = 2_000 * NANOS_PER_MILLI, actual = finished.durationNanos)
    }

    @Test
    fun `a lane that never finishes becomes a DNF and closes the heat as a timeout`() = runBlocking {
        val harness = harness(laneTimeoutMs = 100)
        val heat = harness.armedHeat()
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.port.push(line = "A;START;1000")
        harness.port.push(line = "B;START;1000")
        harness.port.push(line = "A;FINISH;2000")

        assertEquals(
            expected = MeasurementGatewayEvent.LaneFinished(
                heatId = heat.id,
                lane = 1,
                durationNanos = 2_000 * NANOS_PER_MILLI,
                outcome = LaneOutcome.FINISHED,
            ),
            actual = harness.nextEvent(),
        )
        assertEquals(
            expected = MeasurementGatewayEvent.LaneFinished(
                heatId = heat.id,
                lane = 2,
                durationNanos = 0L,
                outcome = LaneOutcome.DNF,
            ),
            actual = harness.nextEvent(),
        )
        assertEquals(expected = MeasurementGatewayEvent.HeatTimeout(heatId = heat.id), actual = harness.nextEvent())
    }

    @Test
    fun `unknown states and malformed lines leave the race state untouched`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.port.push(line = "A;RUN;0")
        harness.port.push(line = "A;WOBBLE;42")
        harness.port.push(line = "not a device line")
        harness.port.push(line = "A;START;1000")
        harness.port.push(line = "A;FINISH;1000")

        val finished = assertIs<MeasurementGatewayEvent.LaneFinished>(value = harness.nextEvent())
        assertEquals(expected = 1_000 * NANOS_PER_MILLI, actual = finished.durationNanos)
        assertEquals(expected = MeasurementGatewayEvent.HeatFinished(heatId = heat.id), actual = harness.nextEvent())
    }

    /** Replays a real heat off `raw-timing.log`: the board reports `START` a few ms
        after `ARM` and then an elapsed `FINISH`. Both lanes must produce their exact
        times — this is the sequence that used to fail as a technical error. */
    @Test
    fun `replays a real capture into two exact lane times`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat()
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.port.push(line = "A;ARM;0")
        harness.port.push(line = "A;START;142878")
        harness.port.push(line = "B;ARM;0")
        harness.port.push(line = "B;START;143519")
        harness.port.push(line = "B;FINISH;1397")
        harness.port.push(line = "B;LOCK;0")
        harness.port.push(line = "A;FINISH;2287")
        harness.port.push(line = "A;LOCK;0")

        val first = assertIs<MeasurementGatewayEvent.LaneFinished>(value = harness.nextEvent())
        assertEquals(expected = 2, actual = first.lane)
        assertEquals(expected = 1_397 * NANOS_PER_MILLI, actual = first.durationNanos)
        val second = assertIs<MeasurementGatewayEvent.LaneFinished>(value = harness.nextEvent())
        assertEquals(expected = 1, actual = second.lane)
        assertEquals(expected = 2_287 * NANOS_PER_MILLI, actual = second.durationNanos)
        assertEquals(expected = MeasurementGatewayEvent.HeatFinished(heatId = heat.id), actual = harness.nextEvent())
    }

    @Test
    fun `a board reset between heats does not void the next heat`() = runBlocking {
        val harness = harness()
        val firstHeat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = firstHeat)
        harness.nextEvent()
        harness.port.push(line = "A;START;225560")
        harness.port.push(line = "A;FINISH;2634")
        harness.nextEvent()
        harness.nextEvent()

        // The board restarts and re-announces itself; uptime begins again from zero.
        harness.port.pushReadyBanner()
        val secondHeat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = secondHeat)
        harness.nextEvent()
        harness.port.push(line = "A;START;16471")
        harness.port.push(line = "A;FINISH;2255")

        val finished = assertIs<MeasurementGatewayEvent.LaneFinished>(value = harness.nextEvent())
        assertEquals(expected = 2_255 * NANOS_PER_MILLI, actual = finished.durationNanos)
        assertEquals(
            expected = MeasurementGatewayEvent.HeatFinished(heatId = secondHeat.id),
            actual = harness.nextEvent(),
        )
    }

    @Test
    fun `a finish with no measured time is a sensor fault, not a zero-second lap`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.port.push(line = "A;START;3565")
        harness.port.push(line = "A;FINISH;0")

        val error = assertIs<MeasurementGatewayEvent.Error>(value = harness.nextEvent())
        assertContains(charSequence = error.message, other = "sensor fault")
        assertNull(actual = harness.nextEventOrNull())
    }

    @Test
    fun `a start milliseconds after arming is a normal measurement by default`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        // The observed board triggers START 4-8 ms after ARM on every heat.
        harness.port.push(line = "A;START;142878")
        harness.port.push(line = "A;FINISH;2287")

        val finished = assertIs<MeasurementGatewayEvent.LaneFinished>(value = harness.nextEvent())
        assertEquals(expected = 2_287 * NANOS_PER_MILLI, actual = finished.durationNanos)
    }

    @Test
    fun `a finish without a recorded start is discarded rather than timed against the host clock`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.port.push(line = "A;FINISH;9999")

        assertNull(actual = harness.nextEventOrNull())
    }

    @Test
    fun `board uptime dropping mid-heat voids the heat`() = runBlocking {
        val harness = harness(finishSemantics = FinishSemantics.TIMESTAMP)
        val heat = harness.armedHeat()
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.port.push(line = "A;START;50000")
        harness.port.push(line = "B;START;120")

        val error = assertIs<MeasurementGatewayEvent.Error>(value = harness.nextEvent())
        assertContains(charSequence = error.message, other = "reset")
    }

    @Test
    fun `a lock while a lane is armed is not treated as a fault`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        // The board echoes our own CMD;A;LOCK and re-announces itself; neither may
        // interfere with a heat that is measuring normally.
        harness.port.push(line = "A;LOCK;0")
        harness.port.push(line = "B;LOCK;0")
        harness.port.push(line = "A;START;142878")
        harness.port.push(line = "A;FINISH;2287")

        val finished = assertIs<MeasurementGatewayEvent.LaneFinished>(value = harness.nextEvent())
        assertEquals(expected = 2_287 * NANOS_PER_MILLI, actual = finished.durationNanos)
    }

    @Test
    fun `losing the port mid-heat voids the heat`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat()
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.port.disconnect()

        val error = assertIs<MeasurementGatewayEvent.Error>(value = harness.nextEvent())
        assertContains(charSequence = error.message, other = "disconnected")
    }

    @Test
    fun `cancel locks the lanes and ignores later device events`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat()
        harness.gateway.start(heat = heat)
        harness.nextEvent()
        harness.port.push(line = "A;START;1000")
        harness.port.written.clear()

        val result = harness.gateway.cancel(heatId = heat.id)

        assertEquals(expected = GatewayCancelResult.Success, actual = result)
        assertEquals(expected = listOf("CMD;A;LOCK", "CMD;B;LOCK"), actual = harness.port.written.toList())
        harness.port.push(line = "A;FINISH;4000")
        assertNull(actual = harness.nextEventOrNull())
    }

    @Test
    fun `cancel reports an error for a heat the device does not hold`() = runBlocking {
        val harness = harness()

        val result = harness.gateway.cancel(heatId = UUID.randomUUID())

        assertIs<GatewayCancelResult.Error>(value = result)
    }

    @Test
    fun `every line received and sent is written to the raw log`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()
        harness.port.push(line = "A;START;1000")
        harness.port.push(line = "A;FINISH;2000")
        harness.nextEvent()
        harness.nextEvent()

        val logged = harness.rawLogPath.readText().trim().lines().map { it.substringAfter(delimiter = " ") }

        assertEquals(
            expected = listOf(
                "IN A;LOCK;0",
                "IN B;LOCK;0",
                "OUT CMD;A;LOCK",
                "OUT CMD;A;ARM",
                "IN A;START;1000",
                "IN A;FINISH;2000",
            ),
            actual = logged,
        )
    }

    @Test
    fun `sendRaw puts an undocumented command on the wire verbatim`() = runBlocking {
        val harness = harness()
        harness.port.pushReadyBanner()

        harness.gateway.sendRaw(command = "CMD;A;RUN")

        assertEquals(expected = listOf("CMD;A;RUN"), actual = harness.port.written.toList())
    }

    private fun harness(
        finishSemantics: FinishSemantics = FinishSemantics.ELAPSED,
        falseStartWindowMs: Long = 0,
        readyTimeoutMs: Long = 2_000,
        laneTimeoutMs: Long = 5_000,
        openFailure: String? = null,
    ): Harness {
        val harness = Harness(
            config = ArduinoTwoLaneSettings(
                portName = "fake",
                readyTimeoutMs = readyTimeoutMs,
                falseStartWindowMs = falseStartWindowMs,
                finishSemantics = finishSemantics,
            ),
            laneTimeoutMs = laneTimeoutMs,
            openFailure = openFailure,
        )
        harnesses += harness
        return harness
    }

    private fun heatWithLanes(laneCount: Int): HeatEntity {
        val now = Clock.System.now()
        return HeatEntity(
            id = UUID.randomUUID(),
            eventId = UUID.randomUUID(),
            round = 1,
            heatNumber = 1,
            status = HeatStatus.ARMED,
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
            armedAt = now,
        )
    }

    /** Gateway under test wired to a fake board, with its events drained into a
        channel so each test can assert on them in order. */
    private inner class Harness(
        config: ArduinoTwoLaneSettings,
        laneTimeoutMs: Long,
        openFailure: String?,
    ) {
        val port = FakeTwoLaneSerialPort(openFailure = openFailure)
        val rawLogPath = Files.createTempFile("raw-timing", ".log").also { it.deleteIfExists() }
        private val scope = CoroutineScope(context = Dispatchers.Unconfined)
        val gateway = TwoLaneSerialMeasurementGateway(
            port = port,
            config = config,
            laneTimeoutMs = laneTimeoutMs,
            rawLog = RawTimingLog(path = rawLogPath),
            scope = scope,
        )
        private val events = Channel<MeasurementGatewayEvent>(capacity = Channel.UNLIMITED)
        private val collector: Job = scope.launch {
            gateway.events().collect { event -> events.send(element = event) }
        }

        suspend fun armedHeat(laneCount: Int = 2): HeatEntity {
            port.pushReadyBanner()
            val heat = heatWithLanes(laneCount = laneCount)
            assertEquals(expected = GatewayArmResult.Success, actual = gateway.arm(heat = heat))
            return heat
        }

        suspend fun nextEvent(): MeasurementGatewayEvent {
            return withTimeout(timeMillis = 2_000) { events.receive() }
        }

        /** Null when nothing arrives — used to assert that a fault produced no result. */
        suspend fun nextEventOrNull(): MeasurementGatewayEvent? {
            return withTimeoutOrNull(timeMillis = 150) { events.receive() }
        }

        suspend fun close() {
            collector.cancel()
            gateway.close()
            rawLogPath.deleteIfExists()
        }

        init {
            // Let the gateway subscribe to its own event flow before any device line
            // is pushed, so nothing is emitted into the void.
            runBlocking { yield() }
        }
    }
}
