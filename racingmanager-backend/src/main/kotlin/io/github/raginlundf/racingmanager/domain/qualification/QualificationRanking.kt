package io.github.raginlundf.racingmanager.domain.qualification

import java.util.UUID

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
