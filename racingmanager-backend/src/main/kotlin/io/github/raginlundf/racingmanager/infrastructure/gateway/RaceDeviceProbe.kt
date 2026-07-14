package io.github.raginlundf.racingmanager.infrastructure.gateway

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.DeviceCommand
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.DeviceEvent
import io.github.raginlundf.racingmanager.infrastructure.gateway.protocol.MessageCodec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage

private val logger = KotlinLogging.logger {}

/** One-shot reachability check for a race-device endpoint, independent of the
running gateway: opens a throwaway WebSocket, sends a `ping`, and waits for the
device's `pong`. Backs the settings UI's "Test connection" button so an
operator can validate a Raspberry Pi address before saving it. */
object RaceDeviceProbe {
    const val DEFAULT_TIMEOUT_MS = 5_000L

    data class ProbeResult(
        val ok: Boolean,
        val pingMs: Long?,
        val error: String?,
    )

    suspend fun testConnection(endpoint: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ProbeResult {
        val connected = CompletableDeferred<WebSocket>()
        val pong = CompletableDeferred<Unit>()
        val client = HttpClient.newHttpClient()
        return runCatching {
            withTimeout(timeMillis = timeoutMs) {
                client.newWebSocketBuilder()
                    .buildAsync(URI.create(endpoint), PongListener(pong = pong))
                    .whenComplete { socket, failure ->
                        if (failure != null) {
                            connected.completeExceptionally(exception = failure)
                        } else {
                            connected.complete(value = socket)
                        }
                    }
                val socket = connected.await()
                val startNanos = System.nanoTime()
                socket.sendText(MessageCodec.encodeCommand(raceId = null, command = DeviceCommand.Ping), true)
                pong.await()
                @Suppress("MagicNumber") val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "probe done")
                ProbeResult(ok = true, pingMs = elapsedMs, error = null)
            }
        }.fold(
            onSuccess = { it },
            onFailure = { ex ->
                when(ex) {
                    is TimeoutCancellationException -> {
                        logger.info { "Race device probe to $endpoint timed out after $timeoutMs ms" }
                        ProbeResult(ok = false, pingMs = null, error = "No response within $timeoutMs ms")
                    }

                    else -> {
                        // Probe boundary: any connection/handshake failure is a failed test,
                        // reported to the operator rather than propagated.
                        logger.info { "Race device probe to $endpoint failed: ${ex.message}" }
                        ProbeResult(ok = false, pingMs = null, error = ex.message ?: "Connection failed")
                    }
                }
            }
        )
    }

    /** Reassembles text frames and completes [pong] on the first `pong` event. */
    private class PongListener(private val pong: CompletableDeferred<Unit>) : WebSocket.Listener {
        private val buffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                val frame = buffer.toString()
                buffer.setLength(0)
                val decoded = runCatching { MessageCodec.decodeEvent(text = frame) }.getOrNull()
                if (decoded?.event is DeviceEvent.Pong) {
                    pong.complete(value = Unit)
                }
            }
            webSocket.request(1)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            pong.completeExceptionally(exception = error)
        }
    }
}
