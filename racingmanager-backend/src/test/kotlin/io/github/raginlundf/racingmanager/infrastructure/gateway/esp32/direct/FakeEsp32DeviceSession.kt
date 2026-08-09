package io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Collections

/** A stand-in for one connected ESP32 module: the test pushes device frames in
    and reads the messages the gateway sent out, so the whole mode is exercised
    with no hardware or real socket. Frames are buffered, so pushing before the
    gateway starts collecting is safe. */
class FakeEsp32DeviceSession : Esp32DeviceSession {
    private val incoming = Channel<String>(capacity = Channel.UNLIMITED)

    /** Every frame the gateway sent to this device, in order. */
    val sent: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())

    @Volatile
    var closeReason: String? = null
        private set

    override suspend fun send(text: String) {
        sent += text
    }

    override fun incoming(): Flow<String> {
        return incoming.receiveAsFlow()
    }

    override suspend fun close(reason: String) {
        closeReason = reason
        incoming.close()
    }

    /** Pushes one raw frame from the device, exactly as it would arrive on the wire. */
    suspend fun push(text: String) {
        incoming.send(element = text)
    }

    /** Simulates the device vanishing — the incoming flow completes. */
    fun disconnect() {
        incoming.close()
    }
}
