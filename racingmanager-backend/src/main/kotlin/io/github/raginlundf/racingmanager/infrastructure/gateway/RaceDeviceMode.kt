package io.github.raginlundf.racingmanager.infrastructure.gateway

/** How the backend reaches the race timing device. */
enum class RaceDeviceMode {
    /** In-process fake Raspberry Pi over a loopback transport — no hardware. */
    SIMULATED,

    /** A real Raspberry Pi reached over a WebSocket connection. */
    HARDWARE,

    /** An Arduino two-lane light barrier on a local USB CDC serial port, speaking
        the semicolon-delimited line protocol from `.plan/Adruino-impl.md`. */
    ARDUINO_TWO_LANE,

    /** Up to 4 ESP32 light-barrier modules dialing into this backend's own
        WebSocket server, speaking the JSON protocol from
        `docs/track_setup/en/PROTOCOL.md`. The reverse connection direction from
        [HARDWARE]: the Raspberry Pi running this backend *is* the timing device's
        server, not a client of one. */
    ESP32_WEBSOCKET_DIRECT,
    ;

    companion object {
        fun from(value: String): RaceDeviceMode {
            return entries.firstOrNull { it.name.equals(other = value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown race-device mode: $value")
        }
    }
}
