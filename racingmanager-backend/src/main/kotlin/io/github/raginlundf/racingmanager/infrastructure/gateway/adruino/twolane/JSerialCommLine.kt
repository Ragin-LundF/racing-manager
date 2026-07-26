package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortEvent
import com.fazecast.jSerialComm.SerialPortMessageListener
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

private val logger = KotlinLogging.logger {}

private val LINE_ENDING = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())

/** A [SerialLine] over a real USB CDC port: 8N1, no flow control, US-ASCII, CRLF
    (`.plan/Adruino-impl.md` §1). Framing is left to jSerialComm's message listener,
    which reassembles fragmented reads up to the CRLF delimiter for us. Writes go
    through a mutex so commands never interleave on the wire (§5.1). */
class JSerialCommLine(
    private val portName: String,
    private val baudRate: Int,
) : SerialLine {
    // Resolved lazily so an unusable port name surfaces from open() as an IOException
    // rather than blowing up in the constructor.
    private val port: SerialPort by lazy { SerialPort.getCommPort(portName) }
    private val writeLock = Mutex()

    override suspend fun open() {
        withContext(Dispatchers.IO) {
            runCatching { port }.getOrElse { throw IOException("Unknown serial port '$portName'", it) }
            port.setComPortParameters(baudRate, DATA_BITS, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
            port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
            if (!port.openPort()) {
                throw IOException("Cannot open serial port '$portName' (baud $baudRate)")
            }
        }
        logger.info { "Opened serial port $portName at $baudRate baud — board resets, awaiting ready banner" }
    }

    override suspend fun write(line: String) {
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                port.outputStream.write(line.toByteArray(Charsets.US_ASCII) + LINE_ENDING)
                port.outputStream.flush()
            }
        }
    }

    /** Single-collector by design: installing the listener starts delivery, and the
        flow completes when the board disconnects so the gateway can invalidate any
        open measurement (§6.2). */
    override fun lines(): Flow<String> {
        return channelFlow {
            val listener = object : SerialPortMessageListener {
                override fun getListeningEvents(): Int {
                    return SerialPort.LISTENING_EVENT_DATA_RECEIVED or SerialPort.LISTENING_EVENT_PORT_DISCONNECTED
                }

                override fun getMessageDelimiter(): ByteArray {
                    return LINE_ENDING
                }

                override fun delimiterIndicatesEndOfMessage(): Boolean {
                    return true
                }

                override fun serialEvent(event: SerialPortEvent) {
                    if (event.eventType == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
                        logger.warn { "Serial port $portName disconnected" }
                        close()
                        return
                    }
                    val line = String(event.receivedData, Charsets.US_ASCII).trim()
                    if (line.isEmpty()) return
                    if (trySend(line).isFailure) {
                        logger.error { "Dropped device line '$line': the reader is not keeping up" }
                    }
                }
            }
            port.addDataListener(listener)
            awaitClose { port.removeDataListener() }
        }.buffer(capacity = Channel.UNLIMITED)
    }

    /** Never throws: closing a port that was never resolvable (unplugged board, wrong
        name) must not block shutdown or a switch to another device mode. Kotlin does
        not cache a failed `lazy`, so touching [port] here would re-run — and re-throw —
        `getCommPort`. */
    override suspend fun close() {
        withContext(Dispatchers.IO) {
            runCatching { port.closePort() }
                .onFailure { logger.warn(it) { "Ignoring failure while closing serial port '$portName'" } }
        }
    }

    private companion object {
        const val DATA_BITS = 8
    }
}
