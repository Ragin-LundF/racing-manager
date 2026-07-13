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

