package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.IOException
import java.util.Collections

/** A stand-in for the Arduino board: the test pushes device lines in and reads the
    commands the gateway wrote out, so the whole mode is exercised with no hardware.
    Lines are buffered, so pushing before the gateway starts collecting is safe. */
class FakeTwoLaneSerialPort(private val openFailure: String? = null) : SerialLine {
    private val incoming = Channel<String>(capacity = Channel.UNLIMITED)

    /** Every command the gateway sent, in order. */
    val written: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())

    @Volatile
    var closed: Boolean = false
        private set

    override suspend fun open() {
        openFailure?.let { throw IOException(it) }
    }

    override suspend fun write(line: String) {
        written += line
    }

    override fun lines(): Flow<String> {
        return incoming.receiveAsFlow()
    }

    override suspend fun close() {
        closed = true
        incoming.close()
    }

    /** Pushes one raw device line, exactly as it would arrive on the wire. */
    suspend fun push(line: String) {
        incoming.send(element = line)
    }

    /** The ready banner: both lanes reporting LOCK, roughly 3.4 s after a reset. */
    suspend fun pushReadyBanner() {
        push(line = "A;LOCK;0")
        push(line = "B;LOCK;0")
    }

    /** Simulates the board vanishing — the line flow completes. */
    fun disconnect() {
        incoming.close()
    }
}
