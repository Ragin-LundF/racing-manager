package io.github.raginlundf.racingmanager.api.spectator.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpectatorSnapshotResponseModel(
    val event: SpectatorEventModel,
    val currentHeat: SpectatorHeatModel? = null,
    val upcomingHeats: List<SpectatorHeatModel> = emptyList(),
    val qualificationRankings: List<SpectatorRankingEntryModel> = emptyList(),
    val qualificationStatus: String? = null,
    val knockout: SpectatorKnockoutStateModel? = null,
)

@Serializable
data class SpectatorEventModel(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String,
    val laneType: String,
    val measurementType: String,
)

@Serializable
data class SpectatorHeatModel(
    val id: String,
    val heatNumber: Int,
    val round: Int,
    val status: String,
    val lanes: List<SpectatorLaneModel>,
    val hasResult: Boolean = false,
)

@Serializable
data class SpectatorLaneModel(
    val lane: Int,
    val participantId: String,
    val participantStartNumber: Int,
    val participantFirstName: String,
    val participantLastName: String,
    val durationNanos: Long? = null,
    val outcome: String? = null,
)

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

@Serializable
data class SpectatorKnockoutStateModel(
    val status: String,
    val pairingMode: String,
    val rounds: List<SpectatorKnockoutRoundModel> = emptyList(),
)

@Serializable
data class SpectatorKnockoutRoundModel(
    val roundNumber: Int,
    val matches: List<SpectatorKnockoutMatchModel> = emptyList(),
)

@Serializable
data class SpectatorKnockoutMatchModel(
    val id: String,
    val roundNumber: Int,
    val matchNumber: Int,
    val participant1Id: String? = null,
    val participant2Id: String? = null,
    val winnerId: String? = null,
    val status: String,
    val isBye: Boolean = false,
)

@Serializable
data class SpectatorTokenResponseModel(
    val exchangeCode: String,
    val expiresIn: Long,
)

@Serializable
data class SpectatorExchangeRequestModel(
    val code: String,
)

@Serializable
data class SpectatorExchangeResponseModel(
    val accessToken: String,
    val expiresIn: Long,
    val eventId: String,
)
