package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

/** The `state` field of a device line. Only the identifiers documented in
    `.plan/Adruino-impl.md` §3.2 exist here; anything else is [UNKNOWN] and must
    never be guessed into a known state. */
enum class DeviceState {
    /** Lane locked, idle, ignores the sensor. Two of these are the ready banner. */
    LOCK,

    /** Lane armed, waiting to be triggered. */
    ARM,

    /** Measurement began — the start photo diode was triggered. */
    START,

    /** Measurement ended — the finish photo diode was triggered. */
    FINISH,

    /** Present in the board's flash table, never observed on the wire. Deliberately
        carries no behaviour: the spec marks its meaning open, so it is logged only.
        // ponytail: inert on purpose. Give it behaviour once a real capture shows
        // what the board actually does with it. */
    RUN,

    /** Any identifier not listed above. Logged, never interpreted. */
    UNKNOWN,
    ;

    companion object {
        fun from(value: String): DeviceState {
            return entries.firstOrNull { it != UNKNOWN && it.name == value } ?: UNKNOWN
        }
    }
}
