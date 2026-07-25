package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwoLaneSerialProbeTest {

    @Test
    fun `reports success once both lanes report the ready banner`() = runBlocking {
        val port = FakeTwoLaneSerialPort()
        launch { port.pushReadyBanner() }

        val result = TwoLaneSerialProbe.testConnection(port = port, readyTimeoutMs = 2_000)

        assertTrue(actual = result.ok)
        assertNotNull(actual = result.pingMs)
        assertNull(actual = result.error)
        assertTrue(actual = port.closed, message = "the probe must not leave the port open")
    }

    @Test
    fun `reports a failure when only one lane reports and the banner never completes`() = runBlocking {
        val port = FakeTwoLaneSerialPort()
        port.push(line = "A;LOCK;0")

        val result = TwoLaneSerialProbe.testConnection(port = port, readyTimeoutMs = 100)

        assertEquals(expected = false, actual = result.ok)
        assertNull(actual = result.pingMs)
        assertEquals(expected = "No ready banner within 100 ms", actual = result.error)
        assertTrue(actual = port.closed)
    }

    @Test
    fun `reports a failure when the port cannot be opened`() = runBlocking {
        val port = FakeTwoLaneSerialPort(openFailure = "Cannot open serial port 'COM9'")

        val result = TwoLaneSerialProbe.testConnection(port = port, readyTimeoutMs = 100)

        assertEquals(expected = false, actual = result.ok)
        assertEquals(expected = "Cannot open serial port 'COM9'", actual = result.error)
    }

    @Test
    fun `rejects settings without a selected port before touching hardware`() = runBlocking {
        val result = TwoLaneSerialProbe.testConnection(settings = ArduinoTwoLaneSettings())

        assertEquals(expected = false, actual = result.ok)
        assertEquals(expected = "No serial port selected", actual = result.error)
    }
}
