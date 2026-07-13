package io.github.raginlundf.racingmanager.infrastructure.gateway.transport

import kotlinx.coroutines.flow.Flow

/** Moves opaque protocol frames (JSON strings) between the adapter and the device.
    The adapter owns the codec; the transport only knows how to send and receive
    text. A real implementation talks WebSocket to the Pi; a loopback one wires an
    in-process fake controller. */
interface RaceDeviceTransport {
    suspend fun connect()
    suspend fun send(text: String)
    fun incoming(): Flow<String>
    suspend fun close()
}
