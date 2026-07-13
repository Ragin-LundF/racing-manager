package io.github.raginlundf.racingmanager.infrastructure.gateway.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MessageCodecTest {

    @Test
    fun `encodes a command as a flat envelope with type and meta at top level`() {
        val text = MessageCodec.encodeCommand(
            raceId = "race-1",
            command = DeviceCommand.PrepareRace(lanes = listOf(1, 2), finishTimeoutMs = 30_000),
        )

        assertTrue(text.contains(other = "\"protocolVersion\":$PROTOCOL_VERSION"))
        assertTrue(text.contains(other = "\"type\":\"prepareRace\""))
        assertTrue(text.contains(other = "\"raceId\":\"race-1\""))
        assertTrue(text.contains(other = "\"lanes\":[1,2]"))
    }

    @Test
    fun `command round-trips through encode and decode`() {
        val original = DeviceCommand.PrepareRace(lanes = listOf(1, 2), finishTimeoutMs = 15_000)
        val decoded = MessageCodec.decodeCommand(MessageCodec.encodeCommand(raceId = "race-42", command = original))

        assertEquals(expected = "race-42", actual = decoded.raceId)
        assertEquals(expected = original, actual = decoded.command)
        assertTrue(decoded.messageId.isNotBlank())
    }

    @Test
    fun `event round-trips through encode and decode`() {
        val original = DeviceEvent.FinishDetected(
            lane = 1,
            finishSequence = 1,
            finishMonotonicNs = 1_234L,
            elapsedNs = 3_287_100L,
        )
        val decoded = MessageCodec.decodeEvent(MessageCodec.encodeEvent(raceId = "race-7", event = original))

        assertEquals(expected = "race-7", actual = decoded.raceId)
        assertEquals(expected = original, actual = decoded.event)
    }

    @Test
    fun `data object command round-trips`() {
        val decoded = MessageCodec.decodeCommand(MessageCodec.encodeCommand(raceId = "r", command = DeviceCommand.StartRace))
        assertEquals(expected = DeviceCommand.StartRace, actual = decoded.command)
    }

    @Test
    fun `rejects a frame with a mismatched protocol version`() {
        val text = """{"protocolVersion":99,"messageId":"m1","raceId":"race-1","timestamp":"t","payload":{"type":"startRace"}}"""

        val ex = assertFailsWith<DeviceProtocolException> { MessageCodec.decodeCommand(text) }
        assertEquals(expected = "race-1", actual = ex.raceId)
    }

    @Test
    fun `maps an unknown event type to a protocol exception carrying the raceId`() {
        val text = """{"protocolVersion":$PROTOCOL_VERSION,"messageId":"m1","raceId":"race-9","timestamp":"t","payload":{"type":"nonsense"}}"""

        val ex = assertFailsWith<DeviceProtocolException> { MessageCodec.decodeEvent(text) }
        assertEquals(expected = "race-9", actual = ex.raceId)
    }
}
