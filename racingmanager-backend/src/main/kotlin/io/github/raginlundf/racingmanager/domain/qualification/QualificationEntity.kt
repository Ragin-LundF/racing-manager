package io.github.raginlundf.racingmanager.domain.qualification

import kotlin.time.Instant
import java.util.UUID

data class QualificationEntity(
    val id: UUID,
    val eventId: UUID,
    val status: QualificationStatus = QualificationStatus.PENDING,
    val numberOfRuns: Int = 2,
    val seed: Long,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val finalizedAt: Instant? = null,
    val finalizedBy: UUID? = null,
)

