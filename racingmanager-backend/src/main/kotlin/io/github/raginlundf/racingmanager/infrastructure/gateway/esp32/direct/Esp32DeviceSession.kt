package io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct

import kotlinx.coroutines.flow.Flow

/** Moves opaque protocol frames (JSON strings) between the gateway and one
    connected ESP32 module. Mirrors
    [io.github.raginlundf.racingmanager.infrastructure.gateway.transport.RaceDeviceTransport],
    but inbound: the device dials in, so there is no `connect()`/reconnect here —
    only send/receive/close for the lifetime of one socket. */
interface Esp32DeviceSession {
    suspend fun send(text: String)
    fun incoming(): Flow<String>
    suspend fun close(reason: String)
}
