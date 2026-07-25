package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

/** The lane identifier the device uses (`.plan/Adruino-impl.md` §2.1). The board
    has exactly these two.
    // ponytail: two lanes is the hardware ceiling, not a simplification. A wider
    // board would need a new protocol capture before adding identifiers here. */
enum class TwoLaneLane {
    A,
    B,
    ;

    companion object {
        fun from(value: String): TwoLaneLane? {
            return entries.firstOrNull { it.name == value }
        }
    }
}
