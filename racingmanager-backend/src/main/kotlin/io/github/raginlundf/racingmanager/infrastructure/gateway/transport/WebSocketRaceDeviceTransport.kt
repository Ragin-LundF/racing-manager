package io.github.raginlundf.racingmanager.infrastructure.gateway.transport

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage

private val logger = KotlinLogging.logger {}

/** Persistent WebSocket connection to a real Raspberry Pi (raspberry.md §2). Text
    frames are reassembled and republished on [incoming]; the socket auto-reconnects
    with a fixed backoff on close or error.
    // ponytail: hello/state resync after reconnect (raspberry.md §7) is wired during
    // real-hardware bring-up — it needs the adapter's codec, which this dumb frame
    // transport deliberately does not own. */
class WebSocketRaceDeviceTransport(
    private val endpoint: String,
    private val scope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
    private val reconnectDelayMs: Long = DEFAULT_RECONNECT_DELAY_MS,
) : RaceDeviceTransport {
    private val incoming = MutableSharedFlow<String>(extraBufferCapacity = 256)
    private val client: HttpClient = HttpClient.newHttpClient()

    @Volatile
    private var socket: WebSocket? = null

    override suspend fun connect() {
        openSocket()
    }

    override suspend fun send(text: String) {
        val current = socket ?: throw IllegalStateException("Race device is not connected")
        current.sendText(text, true)
    }

    override fun incoming(): Flow<String> {
        return incoming.asSharedFlow()
    }

    override suspend fun close() {
        // Cancel first so the listener's onClose/onError cannot schedule a
        // reconnect after we have intentionally torn the connection down.
        scope.cancel()
        socket?.sendClose(WebSocket.NORMAL_CLOSURE, "closing")
        socket = null
    }

    private fun openSocket() {
        client.newWebSocketBuilder()
            .buildAsync(URI.create(endpoint), FrameListener())
            .whenComplete { established, failure ->
                if (failure != null) {
                    logger.warn(throwable = failure) { "Race device connect to $endpoint failed; retrying" }
                    scheduleReconnect()
                } else {
                    logger.info { "Connected to race device at $endpoint" }
                    socket = established
                }
            }
    }

    private fun scheduleReconnect() {
        socket = null
        scope.launch {
            if (!isActive) {
                return@launch
            }
            delay(timeMillis = reconnectDelayMs)
            openSocket()
        }
    }

    /** Reassembles possibly-fragmented text frames and republishes whole messages. */
    private inner class FrameListener : WebSocket.Listener {
        private val buffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                incoming.tryEmit(value = buffer.toString())
                buffer.setLength(0)
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            logger.info { "Race device socket closed ($statusCode $reason); reconnecting" }
            scheduleReconnect()
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            logger.warn(throwable = error) { "Race device socket error; reconnecting" }
            scheduleReconnect()
        }
    }

    companion object {
        const val DEFAULT_RECONNECT_DELAY_MS = 3_000L
    }
}
