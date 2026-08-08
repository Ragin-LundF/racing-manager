package io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow

/** Wraps a live Ktor server WebSocket session — one connected ESP32 module — as
    an [Esp32DeviceSession]. Non-text frames (ping/pong/binary) are dropped rather
    than surfaced: the protocol is JSON text only. */
class KtorEsp32DeviceSession(private val session: DefaultWebSocketServerSession) : Esp32DeviceSession {
    override suspend fun send(text: String) {
        session.send(text)
    }

    override fun incoming(): Flow<String> {
        return session.incoming.receiveAsFlow()
            .filterIsInstance<Frame.Text>()
            .map { it.readText() }
    }

    override suspend fun close(reason: String) {
        session.close(CloseReason(code = CloseReason.Codes.NORMAL, message = reason))
    }
}
