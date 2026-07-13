package io.github.raginlundf.racingmanager.infrastructure.gateway

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RaceDeviceProbeTest {

    @Test
    fun `testConnection reports failure for an unreachable endpoint`() = runBlocking {
        // Port 1 is not listening: the handshake fails (or times out) and the probe
        // must map that to a failed result rather than throwing.
        val result = RaceDeviceProbe.testConnection(endpoint = "ws://127.0.0.1:1/race", timeoutMs = 1_000)

        assertFalse(actual = result.ok)
        assertNull(actual = result.pingMs)
        assertNotNull(actual = result.error)
    }
}
