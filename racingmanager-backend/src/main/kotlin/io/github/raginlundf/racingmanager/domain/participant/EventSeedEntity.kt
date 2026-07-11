package io.github.raginlundf.racingmanager.domain.participant

import kotlin.time.Instant
import java.util.UUID

data class EventSeedEntity(
    val eventId: UUID,
    val seed: Long,
    val randomizedAt: Instant,
    val randomizedBy: UUID,
)
