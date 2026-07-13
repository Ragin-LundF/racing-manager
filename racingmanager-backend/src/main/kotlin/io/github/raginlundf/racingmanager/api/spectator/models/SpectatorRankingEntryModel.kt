package io.github.raginlundf.racingmanager.api.spectator.models

import kotlinx.serialization.Serializable

@Serializable
data class SpectatorRankingEntryModel(
    val participantId: String,
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
