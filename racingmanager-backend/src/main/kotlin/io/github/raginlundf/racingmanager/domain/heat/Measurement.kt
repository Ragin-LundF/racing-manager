package io.github.raginlundf.racingmanager.domain.heat

import kotlin.time.Instant
import java.util.UUID

data class Measurement(
    val id: UUID,
    val heatId: UUID,
    val lane: Int,
    val durationNanos: Long,
    val outcome: LaneOutcome,
    val receivedAt: Instant,
)
