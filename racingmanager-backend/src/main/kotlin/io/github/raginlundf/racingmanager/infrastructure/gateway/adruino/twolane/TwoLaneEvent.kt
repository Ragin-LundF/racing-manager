package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

import kotlin.time.Instant

/** One parsed device line. [value] is a board `millis()` value for `START` (and,
    depending on [FinishSemantics], for `FINISH`) and 0 for the state events.
    [hostTimestamp] is for logging and for the false-start window only — it never
    contributes to a measured race time (`.plan/Adruino-impl.md` §4.3). */
data class TwoLaneEvent(
    val lane: TwoLaneLane,
    val state: DeviceState,
    val value: Long,
    val raw: String,
    val hostTimestamp: Instant,
)
