package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceProbe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.time.Clock

/** Validates an Arduino two-lane connection before it is saved: opens the port and
    waits for the ready banner, which is the board's only readiness signal — this
    protocol has no ping and no heartbeat (`.plan/Adruino-impl.md` §3.1). Always
    closes the port again, so probing never leaves the device connected. */
object TwoLaneSerialProbe {

    suspend fun testConnection(settings: ArduinoTwoLaneSettings): RaceDeviceProbe.ProbeResult {
        if (settings.portName.isBlank()) {
            return RaceDeviceProbe.ProbeResult(ok = false, pingMs = null, error = "No serial port selected")
        }
        return testConnection(
            port = JSerialCommLine(portName = settings.portName, baudRate = settings.baudRate),
            readyTimeoutMs = settings.readyTimeoutMs,
        )
    }

    suspend fun testConnection(port: SerialLine, readyTimeoutMs: Long): RaceDeviceProbe.ProbeResult {
        val startedAt = Clock.System.now()
        return try {
            port.open()
            val banner = withTimeoutOrNull(timeMillis = readyTimeoutMs) { awaitBanner(port = port) }
            if (banner == null) {
                RaceDeviceProbe.ProbeResult(
                    ok = false,
                    pingMs = null,
                    error = "No ready banner within $readyTimeoutMs ms",
                )
            } else {
                RaceDeviceProbe.ProbeResult(
                    ok = true,
                    pingMs = (Clock.System.now() - startedAt).inWholeMilliseconds,
                    error = null,
                )
            }
        } catch (failure: IOException) {
            RaceDeviceProbe.ProbeResult(ok = false, pingMs = null, error = failure.message ?: "Cannot open the port")
        } finally {
            runCatching { port.close() }
        }
    }

    /** Suspends until both lanes have reported LOCK — that pair is the ready banner. */
    private suspend fun awaitBanner(port: SerialLine) {
        val seen = mutableSetOf<TwoLaneLane>()
        port.lines().first { line ->
            val event = TwoLaneLineParser.parse(line = line, hostTimestamp = Clock.System.now())
            if (event != null && event.state == DeviceState.LOCK) seen += event.lane
            seen.containsAll(TwoLaneLane.entries)
        }
    }
}
