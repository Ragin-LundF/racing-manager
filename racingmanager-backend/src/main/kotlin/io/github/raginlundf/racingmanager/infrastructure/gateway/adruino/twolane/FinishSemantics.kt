package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

/** How to read the `value` field of a `FINISH` event. The spec marks this open
    (`.plan/Adruino-impl.md` §4.2) — it is a setting, never a guess, and the first
    real measurement resolves it: a value in the same range as the `START` values
    means [TIMESTAMP], a small uptime-independent value means [ELAPSED]. */
enum class FinishSemantics {
    /** `value` is a board `millis()` timestamp — duration is `finish - start`. */
    TIMESTAMP,

    /** `value` is already the elapsed time in milliseconds. */
    ELAPSED,
    ;

    companion object {
        fun from(value: String): FinishSemantics {
            return entries.firstOrNull { it.name.equals(other = value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown finish semantics: $value")
        }
    }
}
