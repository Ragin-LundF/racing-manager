package io.github.raginlundf.racingmanager.domain.bootstrap

import kotlin.time.Instant
import java.util.UUID

data class LocalInstanceEntity(
    val id: UUID,
    val createdAt: Instant,
)
