package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

import kotlinx.serialization.Serializable

/** Connection and interpretation settings for the Arduino two-lane light barrier.
    Defaults are the suggested values from `.plan/Adruino-impl.md` §7.1; every
    inferred or open protocol detail is a knob here rather than a constant in the
    code, as §0 requires. Persisted as one JSON column on the race-device settings
    row, so serialization is part of the contract. */
@Serializable
data class ArduinoTwoLaneSettings(
    /** OS port name: `COM3`, `/dev/tty.usbmodem1101`, `/dev/ttyACM0`. Never hard-coded. */
    val portName: String = "",
    val baudRate: Int = DEFAULT_BAUD_RATE,
    /** How long to wait for the board's ready banner after opening the port; the
        board resets on DTR assert and needs roughly 3.4 s to boot (§1.1). */
    val readyTimeoutMs: Long = DEFAULT_READY_TIMEOUT_MS,
    /** A `START` this soon after `ARM` is treated as a false start or a stuck sensor
        rather than a measurement (§6.1). Disabled by default (0): on the observed
        board `START` fires 4–8 ms after every `ARM` while the finish times are
        perfectly valid, so the §6.1 heuristic would reject every real heat. What
        does catch the §6.1 fault is a zero-length measurement, which is always
        rejected. Raise this only for a board that arms without triggering. */
    val falseStartWindowMs: Long = DEFAULT_FALSE_START_WINDOW_MS,
    /** [FinishSemantics.ELAPSED] — established from nine measurements in a real
        capture, each matching the host-side interval within 40 ms. */
    val finishSemantics: FinishSemantics = FinishSemantics.ELAPSED,
    /** Raw log of every line sent and received — mandatory while §4.2 and `RUN`
        remain open, since it is the only way to re-evaluate a race afterwards (§6.3). */
    val rawLogPath: String = DEFAULT_RAW_LOG_PATH,
) {
    companion object {
        const val DEFAULT_BAUD_RATE = 115_200
        const val DEFAULT_READY_TIMEOUT_MS = 10_000L
        const val DEFAULT_FALSE_START_WINDOW_MS = 0L
        const val DEFAULT_RAW_LOG_PATH = "raw-timing.log"
    }
}
