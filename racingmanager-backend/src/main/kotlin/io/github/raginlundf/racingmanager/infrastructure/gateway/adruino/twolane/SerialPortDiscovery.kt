package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

import com.fazecast.jSerialComm.SerialPort

/** One serial port offered to the operator for selection. */
data class SerialPortInfo(
    val name: String,
    val description: String,
)

/** Enumerates the machine's serial ports so the UI can offer them instead of the
    operator typing `COM3` / `/dev/tty.usbmodem1101` / `/dev/ttyACM0` by hand — the
    port name must not be hard-coded anywhere (`.plan/Adruino-impl.md` §1). */
object SerialPortDiscovery {

    fun availablePorts(): List<SerialPortInfo> {
        return SerialPort.getCommPorts().map {
            SerialPortInfo(name = it.systemPortName, description = it.descriptivePortName)
        }
    }
}
