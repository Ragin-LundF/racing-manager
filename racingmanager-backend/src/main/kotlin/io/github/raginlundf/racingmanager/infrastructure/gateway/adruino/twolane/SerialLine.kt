package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

import kotlinx.coroutines.flow.Flow

/** A line-oriented serial connection. The only hardware-touching seam in this
    package, so the gateway is testable against a fake device with no board
    attached. Implementations must serialise writes: a serial port is not
    thread-safe and interleaved lines would end up on the wire
    (`.plan/Adruino-impl.md` §5.1). */
interface SerialLine {
    /** Opens the port. Asserting DTR resets the board, so this is called once per
        connection and never again while a race is in progress (§1.1). */
    suspend fun open()

    /** Writes one command; the implementation appends the line ending. */
    suspend fun write(line: String)

    /** Received lines, without their line ending. Completes when the port closes. */
    fun lines(): Flow<String>

    suspend fun close()
}
