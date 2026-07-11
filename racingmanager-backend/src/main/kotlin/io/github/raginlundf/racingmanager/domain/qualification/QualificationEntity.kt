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

data class QualificationRanking(
    val participantId: UUID,
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
    val bestTimeNanos: Long? = null,
    val totalTimeNanos: Long? = null,
    val completedRuns: Int = 0,
    val dnfCount: Int = 0,
    val rank: Int = 0,
)
