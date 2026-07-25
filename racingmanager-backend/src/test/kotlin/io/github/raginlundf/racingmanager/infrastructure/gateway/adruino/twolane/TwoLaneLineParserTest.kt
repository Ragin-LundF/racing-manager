package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TwoLaneLineParserTest {

    private val now = Instant.fromEpochMilliseconds(epochMilliseconds = 1_700_000_000_000)

    private fun parse(line: String): TwoLaneEvent? {
        return TwoLaneLineParser.parse(line = line, hostTimestamp = now)
    }

    @Test
    fun `parses a start event with its board millis value`() {
        val event = parse(line = "A;START;3565")

        assertEquals(
            expected = TwoLaneEvent(
                lane = TwoLaneLane.A,
                state = DeviceState.START,
                value = 3565,
                raw = "A;START;3565",
                hostTimestamp = now,
            ),
            actual = event,
        )
    }

    @Test
    fun `parses the ready banner lines`() {
        assertEquals(expected = DeviceState.LOCK, actual = parse(line = "B;LOCK;0")?.state)
        assertEquals(expected = TwoLaneLane.B, actual = parse(line = "B;LOCK;0")?.lane)
    }

    @Test
    fun `trims the flash table's four-character padding`() {
        assertEquals(expected = DeviceState.ARM, actual = parse(line = "A;ARM ;0")?.state)
        assertEquals(expected = DeviceState.RUN, actual = parse(line = "A;RUN ;0")?.state)
    }

    @Test
    fun `maps an undocumented state to UNKNOWN instead of guessing`() {
        assertEquals(expected = DeviceState.UNKNOWN, actual = parse(line = "A;WOBBLE;0")?.state)
    }

    @Test
    fun `discards lines that do not have three fields`() {
        assertNull(actual = parse(line = "A;START"))
        assertNull(actual = parse(line = "A;START;10;extra"))
        assertNull(actual = parse(line = ""))
    }

    @Test
    fun `discards a truncated boot artefact line`() {
        assertNull(actual = parse(line = "RT;A;LO"))
    }

    @Test
    fun `discards a non-numeric value`() {
        assertNull(actual = parse(line = "A;START;abc"))
    }

    @Test
    fun `discards a lane the device does not have`() {
        assertNull(actual = parse(line = "C;START;100"))
    }
}
