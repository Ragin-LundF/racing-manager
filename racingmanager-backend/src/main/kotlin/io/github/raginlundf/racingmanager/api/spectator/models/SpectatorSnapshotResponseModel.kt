package io.github.raginlundf.racingmanager.api.spectator.models

import kotlinx.serialization.Serializable

@Serializable
data class SpectatorSnapshotResponseModel(
    val event: SpectatorEventModel,
    val currentHeat: SpectatorHeatModel? = null,
    val upcomingHeats: List<SpectatorHeatModel> = emptyList(),
    val qualificationRankings: List<SpectatorRankingEntryModel> = emptyList(),
    val qualificationStatus: String? = null,
    val knockout: SpectatorKnockoutStateModel? = null,
    val knockoutStandings: List<SpectatorParticipantStandingModel> = emptyList(),
)
