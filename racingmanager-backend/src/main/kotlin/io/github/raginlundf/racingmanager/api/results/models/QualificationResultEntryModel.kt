package io.github.raginlundf.racingmanager.api.results.models

import kotlinx.serialization.Serializable

@Serializable
data class QualificationResultEntryModel(
    val participantId: String,
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
    val bestTimeNanos: Long? = null,
    val totalTimeNanos: Long? = null,
    val completedRuns: Int,
    val dnfCount: Int,
    val rank: Int,
)
