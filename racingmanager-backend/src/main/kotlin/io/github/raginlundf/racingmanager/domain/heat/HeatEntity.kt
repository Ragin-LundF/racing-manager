package io.github.raginlundf.racingmanager.domain.heat

import kotlin.time.Instant
import java.util.UUID

data class HeatEntity(
    val id: UUID,
    val eventId: UUID,
    val round: Int,
    val heatNumber: Int,
    val status: HeatStatus,
    val lanes: List<HeatLaneAssignment>,
    val measurements: List<Measurement>,
    val createdAt: Instant,
    val armedAt: Instant? = null,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
)

data class HeatLaneAssignment(
    val lane: Int,
    val participantId: UUID,
    val participantStartNumber: Int,
    val participantFirstName: String,
    val participantLastName: String,
)

data class Measurement(
    val id: UUID,
    val heatId: UUID,
    val lane: Int,
    val durationNanos: Long,
    val outcome: LaneOutcome,
    val receivedAt: Instant,
)
