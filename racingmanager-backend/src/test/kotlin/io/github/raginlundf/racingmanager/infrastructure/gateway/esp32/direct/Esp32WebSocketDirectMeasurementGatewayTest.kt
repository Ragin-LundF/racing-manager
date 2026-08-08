package io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct

import io.github.raginlundf.racingmanager.application.heat.GatewayArmResult
import io.github.raginlundf.racingmanager.application.heat.GatewayCancelResult
import io.github.raginlundf.racingmanager.application.heat.MeasurementGatewayEvent
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.RawTimingLog
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.protocol.Esp32Message
import io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.protocol.Esp32MessageCodec
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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class Esp32WebSocketDirectMeasurementGatewayTest {

    private val harnesses = mutableListOf<Harness>()

    @AfterTest
    fun tearDown() {
        runBlocking { harnesses.forEach { it.close() } }
    }

    @Test
    fun `arm succeeds locally once both lanes have their start and finish devices connected`() = runBlocking {
        val harness = harness()
        harness.connectAllFour()

        val result = harness.gateway.arm(heat = heatWithLanes(laneCount = 2))

        assertEquals(expected = GatewayArmResult.Success, actual = result)
    }

    @Test
    fun `arm succeeds for a heat that only needs the lanes actually connected`() = runBlocking {
        val harness = harness()
        harness.connect(deviceId = "lane-1-start")
        harness.connect(deviceId = "lane-1-finish")

        val result = harness.gateway.arm(heat = heatWithLanes(laneCount = 1))

        assertEquals(expected = GatewayArmResult.Success, actual = result)
    }

    @Test
    fun `arm fails when a required device is not connected`() = runBlocking {
        val harness = harness()
        harness.connect(deviceId = "lane-1-start")
        harness.connect(deviceId = "lane-1-finish")

        val result = harness.gateway.arm(heat = heatWithLanes(laneCount = 2))

        assertIs<GatewayArmResult.Error>(value = result)
        assertContains(charSequence = result.message, other = "lane-2-start")
    }

    @Test
    fun `arm rejects a heat with more lanes than the hardware has`() = runBlocking {
        val harness = harness()
        harness.connectAllFour()

        val result = harness.gateway.arm(heat = heatWithLanes(laneCount = 3))

        assertIs<GatewayArmResult.Error>(value = result)
    }

    @Test
    fun `arm rejects a second heat while one is still running`() = runBlocking {
        val harness = harness()
        harness.connectAllFour()
        harness.gateway.arm(heat = heatWithLanes(laneCount = 2))

        val result = harness.gateway.arm(heat = heatWithLanes(laneCount = 2))

        assertIs<GatewayArmResult.Error>(value = result)
    }

    @Test
    fun `an unknown device_id is rejected and the socket is closed`() = runBlocking {
        val harness = harness()

        val session = harness.connect(deviceId = "lane-9-start")

        assertEquals(expected = "Unknown device_id", actual = session.closeReason)
    }

    @Test
    fun `start reports the heat as started`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat()

        harness.gateway.start(heat = heat)

        assertEquals(expected = MeasurementGatewayEvent.HeatStarted(heatId = heat.id), actual = harness.nextEvent())
    }

    @Test
    fun `a lane time is the receipt-time delta between its start and finish beam breaks`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.beamBroken(deviceId = "lane-1-start", sequence = 1)
        harness.beamBroken(deviceId = "lane-1-finish", sequence = 1)

        val finished = assertIs<MeasurementGatewayEvent.LaneFinished>(value = harness.nextEvent())
        assertEquals(expected = 1, actual = finished.lane)
        assertEquals(expected = LaneOutcome.FINISHED, actual = finished.outcome)
        assertTrue(actual = finished.durationNanos > 0)
        assertEquals(expected = MeasurementGatewayEvent.HeatFinished(heatId = heat.id), actual = harness.nextEvent())
    }

    @Test
    fun `both lanes finishing independently settles the heat`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat(laneCount = 2)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.beamBroken(deviceId = "lane-1-start", sequence = 1)
        harness.beamBroken(deviceId = "lane-2-start", sequence = 1)
        harness.beamBroken(deviceId = "lane-1-finish", sequence = 1)
        harness.beamBroken(deviceId = "lane-2-finish", sequence = 1)

        val lanes = setOf(
            (harness.nextEvent() as MeasurementGatewayEvent.LaneFinished).lane,
            (harness.nextEvent() as MeasurementGatewayEvent.LaneFinished).lane,
        )
        assertEquals(expected = setOf(1, 2), actual = lanes)
        assertEquals(expected = MeasurementGatewayEvent.HeatFinished(heatId = heat.id), actual = harness.nextEvent())
    }

    @Test
    fun `a finish without a recorded start is discarded rather than timed`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.beamBroken(deviceId = "lane-1-finish", sequence = 1)

        assertNull(actual = harness.nextEventOrNull())
    }

    @Test
    fun `a duplicate sequence from the same boot is ignored`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.beamBroken(deviceId = "lane-1-start", sequence = 5)
        harness.beamBroken(deviceId = "lane-1-start", sequence = 5)
        harness.beamBroken(deviceId = "lane-1-finish", sequence = 1)

        val finished = assertIs<MeasurementGatewayEvent.LaneFinished>(value = harness.nextEvent())
        assertEquals(expected = 1, actual = finished.lane)
    }

    @Test
    fun `a lane that never finishes becomes a DNF and closes the heat as a timeout`() = runBlocking {
        val harness = harness(laneTimeoutMs = 100)
        val heat = harness.armedHeat(laneCount = 2)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.beamBroken(deviceId = "lane-1-start", sequence = 1)
        harness.beamBroken(deviceId = "lane-1-finish", sequence = 1)
        harness.beamBroken(deviceId = "lane-2-start", sequence = 1)

        val finished = assertIs<MeasurementGatewayEvent.LaneFinished>(value = harness.nextEvent())
        assertEquals(expected = 1, actual = finished.lane)
        val timedOut = assertIs<MeasurementGatewayEvent.LaneFinished>(value = harness.nextEvent())
        assertEquals(expected = 2, actual = timedOut.lane)
        assertEquals(expected = LaneOutcome.DNF, actual = timedOut.outcome)
        assertEquals(expected = MeasurementGatewayEvent.HeatTimeout(heatId = heat.id), actual = harness.nextEvent())
    }

    @Test
    fun `a device disconnecting mid-heat fails the heat`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        harness.disconnect(deviceId = "lane-1-finish")

        val error = assertIs<MeasurementGatewayEvent.Error>(value = harness.nextEvent())
        assertEquals(expected = heat.id, actual = error.heatId)
        assertContains(charSequence = error.message, other = "lane-1-finish")
    }

    @Test
    fun `cancel clears the heat and ignores later beam breaks`() = runBlocking {
        val harness = harness()
        val heat = harness.armedHeat(laneCount = 1)
        harness.gateway.start(heat = heat)
        harness.nextEvent()

        val result = harness.gateway.cancel(heatId = heat.id)

        assertEquals(expected = GatewayCancelResult.Success, actual = result)
        harness.beamBroken(deviceId = "lane-1-start", sequence = 1)
        harness.beamBroken(deviceId = "lane-1-finish", sequence = 1)
        assertNull(actual = harness.nextEventOrNull())
    }

    @Test
    fun `cancel reports an error for a heat the gateway does not hold`() = runBlocking {
        val harness = harness()

        val result = harness.gateway.cancel(heatId = UUID.randomUUID())

        assertIs<GatewayCancelResult.Error>(value = result)
    }

    @Test
    fun `constructor refuses settings with the unimplemented handshake or time-sync flags on`() {
        assertTrue(
            actual = runCatching {
                Esp32WebSocketDirectMeasurementGateway(
                    settings = Esp32WebSocketDirectSettings(useRaceControlHandshake = true),
                    laneTimeoutMs = 5_000,
                    rawLog = RawTimingLog(path = Files.createTempFile("raw-esp32-timing", ".log")),
                )
            }.isFailure,
        )
        assertTrue(
            actual = runCatching {
                Esp32WebSocketDirectMeasurementGateway(
                    settings = Esp32WebSocketDirectSettings(useTimeSync = true),
                    laneTimeoutMs = 5_000,
                    rawLog = RawTimingLog(path = Files.createTempFile("raw-esp32-timing", ".log")),
                )
            }.isFailure,
        )
    }

    @Test
    fun `deviceSnapshots reports every expected device, connected or not`() = runBlocking {
        val harness = harness()
        harness.connect(deviceId = "lane-1-start")

        val snapshots = harness.gateway.deviceSnapshots()

        assertEquals(expected = 4, actual = snapshots.size)
        val connected = snapshots.first { it.deviceId == "lane-1-start" }
        assertTrue(actual = connected.connected)
        assertEquals(expected = 1, actual = connected.lane)
        assertEquals(expected = Esp32ModuleRole.START, actual = connected.role)
        val notConnected = snapshots.first { it.deviceId == "lane-2-finish" }
        assertTrue(actual = !notConnected.connected)
    }

    private fun harness(laneTimeoutMs: Long = 5_000): Harness {
        val harness = Harness(laneTimeoutMs = laneTimeoutMs)
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

    /** Gateway under test wired to fake ESP32 sockets, with its events drained into
        a channel so each test can assert on them in order. */
    private inner class Harness(laneTimeoutMs: Long) {
        val rawLogPath = Files.createTempFile("raw-esp32-timing", ".log").also { it.deleteIfExists() }
        private val scope = CoroutineScope(context = Dispatchers.Unconfined)
        val gateway = Esp32WebSocketDirectMeasurementGateway(
            settings = Esp32WebSocketDirectSettings(),
            laneTimeoutMs = laneTimeoutMs,
            rawLog = RawTimingLog(path = rawLogPath),
            scope = scope,
        )
        private val events = Channel<MeasurementGatewayEvent>(capacity = Channel.UNLIMITED)
        private val collector: Job = scope.launch {
            gateway.events().collect { event -> events.send(element = event) }
        }
        private val sessions = mutableMapOf<String, FakeEsp32DeviceSession>()
        private val connectionJobs = mutableListOf<Job>()

        fun isConnected(deviceId: String): Boolean {
            return gateway.deviceSnapshots().any { it.deviceId == deviceId && it.connected }
        }

        /** Pushes `device.register` and waits until the gateway has actually
            processed it — either the device shows up as connected, or the socket
            was closed as rejected. A plain `yield()` after the push is not
            reliable here: [gateway] serves 4 independent sessions, each driven by
            its own launched [handleConnection] coroutine, and nothing otherwise
            orders "registration processed" before the next harness call. */
        suspend fun connect(deviceId: String, bootId: String = DEFAULT_BOOT_ID): FakeEsp32DeviceSession {
            val session = FakeEsp32DeviceSession()
            sessions[deviceId] = session
            connectionJobs += scope.launch { gateway.handleConnection(session = session) }
            val role = deviceId.substringAfterLast(delimiter = "-")
            session.push(
                text = Esp32MessageCodec.encode(
                    message = Esp32Message.DeviceRegister(
                        deviceId = deviceId,
                        bootId = bootId,
                        role = role,
                        firmware = "0.1.0",
                    ),
                ),
            )
            withTimeout(timeMillis = 2_000) {
                while (session.closeReason == null && !isConnected(deviceId = deviceId)) {
                    yield()
                }
            }
            return session
        }

        suspend fun connectAllFour() {
            listOf("lane-1-start", "lane-1-finish", "lane-2-start", "lane-2-finish").forEach { connect(deviceId = it) }
        }

        suspend fun armedHeat(laneCount: Int = 2): HeatEntity {
            connectAllFour()
            val heat = heatWithLanes(laneCount = laneCount)
            assertEquals(expected = GatewayArmResult.Success, actual = gateway.arm(heat = heat))
            return heat
        }

        suspend fun beamBroken(deviceId: String, sequence: Long, bootId: String = DEFAULT_BOOT_ID) {
            val identity = Esp32DeviceIdentity.parse(deviceId = deviceId)!!
            val session = sessions.getValue(key = deviceId)
            session.push(
                text = Esp32MessageCodec.encode(
                    message = Esp32Message.SensorEvent(
                        messageId = UUID.randomUUID().toString(),
                        deviceId = deviceId,
                        bootId = bootId,
                        sequence = sequence,
                        role = identity.role.wireValue,
                        lane = identity.lane,
                        event = "beam_broken",
                        localTimestampUs = sequence * MICROS_PER_EVENT,
                    ),
                ),
            )
            yield()
        }

        suspend fun disconnect(deviceId: String) {
            sessions.getValue(key = deviceId).disconnect()
            yield()
        }

        suspend fun nextEvent(): MeasurementGatewayEvent {
            return withTimeout(timeMillis = 2_000) { events.receive() }
        }

        /** Null when nothing arrives — used to assert that a discarded event or a
            cancelled heat produced no result. */
        suspend fun nextEventOrNull(): MeasurementGatewayEvent? {
            return withTimeoutOrNull(timeMillis = 150) { events.receive() }
        }

        suspend fun close() {
            connectionJobs.forEach { it.cancel() }
            collector.cancel()
            gateway.close()
            rawLogPath.deleteIfExists()
        }
    }

    private companion object {
        const val DEFAULT_BOOT_ID = "boot-1"
        const val MICROS_PER_EVENT = 1_000L
    }
}
