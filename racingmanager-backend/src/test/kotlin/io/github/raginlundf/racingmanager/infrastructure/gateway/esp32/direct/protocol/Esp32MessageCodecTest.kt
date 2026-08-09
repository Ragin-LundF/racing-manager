package io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Esp32MessageCodecTest {

    @Test
    fun `encodes a sensor event with snake_case field names and the dotted type`() {
        val text = Esp32MessageCodec.encode(
            message = Esp32Message.SensorEvent(
                messageId = "d2",
                deviceId = "lane-1-start",
                bootId = "a8c1",
                sequence = 77,
                role = "start",
                lane = 1,
                event = "beam_broken",
                localTimestampUs = 4_567_890L,
            ),
        )

        assertTrue(actual = text.contains(other = "\"type\":\"sensor.event\""))
        assertTrue(actual = text.contains(other = "\"device_id\":\"lane-1-start\""))
        assertTrue(actual = text.contains(other = "\"boot_id\":\"a8c1\""))
        assertTrue(actual = text.contains(other = "\"local_timestamp_us\":4567890"))
    }

    @Test
    fun `sensor event round-trips through encode and decode`() {
        val original = Esp32Message.SensorEvent(
            messageId = "d2",
            deviceId = "lane-1-finish",
            bootId = "a8c1",
            sequence = 3,
            role = "finish",
            lane = 1,
            event = "beam_broken",
            localTimestampUs = 4_567_890L,
        )

        val decoded = Esp32MessageCodec.decodeOrNull(text = Esp32MessageCodec.encode(message = original))

        assertEquals(expected = original, actual = decoded)
    }

    @Test
    fun `sensor event round-trips with the optional race-control and time-sync fields populated`() {
        val original = Esp32Message.SensorEvent(
            messageId = "d3",
            deviceId = "lane-2-finish",
            bootId = "a8c1",
            sequence = 4,
            role = "finish",
            lane = 2,
            event = "beam_broken",
            localTimestampUs = 4_567_890L,
            raceId = "race-42",
            syncTimestampUs = 1_760_000_008_329_470L,
            syncUncertaintyUs = 3_500L,
        )

        val decoded = Esp32MessageCodec.decodeOrNull(text = Esp32MessageCodec.encode(message = original))

        assertEquals(expected = original, actual = decoded)
    }

    @Test
    fun `device register round-trips through encode and decode`() {
        val original = Esp32Message.DeviceRegister(
            deviceId = "lane-1-start",
            bootId = "a8c1",
            role = "start",
            firmware = "0.1.0",
            capabilities = listOf("beam_sensor", "wifi"),
        )

        val decoded = Esp32MessageCodec.decodeOrNull(text = Esp32MessageCodec.encode(message = original))

        assertEquals(expected = original, actual = decoded)
    }

    @Test
    fun `device heartbeat round-trips through encode and decode`() {
        val original = Esp32Message.DeviceHeartbeat(
            deviceId = "lane-1-finish",
            bootId = "a8c1",
            uptimeMs = 64_321L,
            transport = "wifi",
            sensors = mapOf("lane_1" to "clear"),
        )

        val decoded = Esp32MessageCodec.decodeOrNull(text = Esp32MessageCodec.encode(message = original))

        assertEquals(expected = original, actual = decoded)
    }

    @Test
    fun `event ack round-trips through encode and decode`() {
        val original = Esp32Message.EventAck(messageId = "event-uuid")

        val decoded = Esp32MessageCodec.decodeOrNull(text = Esp32MessageCodec.encode(message = original))

        assertEquals(expected = original, actual = decoded)
    }

    @Test
    fun `race control round trip covers arm, armed, start, and reset`() {
        val arm = Esp32Message.RaceArm(raceId = "race-42", lanes = listOf(1, 2), syncEpochUs = 1_760_000_000_000L)
        val armed = Esp32Message.RaceArmed(raceId = "race-42", deviceId = "lane-1-start", sensorsReady = true)
        val start = Esp32Message.RaceStart(raceId = "race-42", startReferenceUs = 1_760_000_005_000L)
        val reset = Esp32Message.RaceReset(raceId = "race-42")

        assertEquals(
            expected = arm,
            actual = Esp32MessageCodec.decodeOrNull(text = Esp32MessageCodec.encode(message = arm)),
        )
        assertEquals(
            expected = armed,
            actual = Esp32MessageCodec.decodeOrNull(text = Esp32MessageCodec.encode(message = armed)),
        )
        assertEquals(
            expected = start,
            actual = Esp32MessageCodec.decodeOrNull(text = Esp32MessageCodec.encode(message = start)),
        )
        assertEquals(
            expected = reset,
            actual = Esp32MessageCodec.decodeOrNull(text = Esp32MessageCodec.encode(message = reset)),
        )
    }

    @Test
    fun `time sync request and response round-trip through encode and decode`() {
        val request = Esp32Message.TimeSyncRequest(nonce = "n", serverSendUs = 1_760_000_000_000L)
        val response = Esp32Message.TimeSyncResponse(nonce = "n", deviceReceiveUs = 900_000L, deviceSendUs = 900_040L)

        assertEquals(
            expected = request,
            actual = Esp32MessageCodec.decodeOrNull(text = Esp32MessageCodec.encode(message = request)),
        )
        assertEquals(
            expected = response,
            actual = Esp32MessageCodec.decodeOrNull(text = Esp32MessageCodec.encode(message = response)),
        )
    }

    @Test
    fun `returns null for malformed json`() {
        val decoded = Esp32MessageCodec.decodeOrNull(text = "not json")

        assertNull(actual = decoded)
    }

    @Test
    fun `returns null for an unknown type`() {
        val decoded = Esp32MessageCodec.decodeOrNull(text = """{"v":1,"type":"nonsense"}""")

        assertNull(actual = decoded)
    }

    @Test
    fun `returns null for a mismatched protocol version`() {
        @Suppress("MaxLineLength")
        val text = """{"v":99,"type":"event.ack","message_id":"m1"}"""

        val decoded = Esp32MessageCodec.decodeOrNull(text = text)

        assertNull(actual = decoded)
    }
}
