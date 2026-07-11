package io.github.raginlundf.racingmanager.api.knockout.models

import kotlinx.serialization.Serializable

@Serializable
data class KnockoutTournamentResponseModel(
    val id: String,
    val eventId: String,
    val status: String,
    val pairingMode: String,
    val qualificationId: String,
    val createdAt: String,
    val updatedAt: String? = null,
    val finalizedAt: String? = null,
    val finalizedBy: String? = null,
)

@Serializable
data class SetupKnockoutRequestModel(
    val pairingMode: String,
)

@Serializable
data class KnockoutMatchResponseModel(
    val id: String,
    val tournamentId: String,
    val roundNumber: Int,
    val matchNumber: Int,
    val participant1Id: String? = null,
    val participant2Id: String? = null,
    val winnerId: String? = null,
    val heatId: String? = null,
    val status: String,
    val createdAt: String,
)

@Serializable
data class RecordMatchResultRequestModel(
    val matchId: String,
    val winnerId: String,
    val heatId: String,
)

@Serializable
data class CreateHeatForMatchRequestModel(
    val matchId: String,
)

@Serializable
data class SetManualPairingsRequestModel(
    val pairings: List<ManualPairingEntryModel>,
)

@Serializable
data class ManualPairingEntryModel(
    val participant1Id: String,
    val participant2Id: String? = null,
)

@Serializable
data class QualifiedParticipantResponseModel(
    val participantId: String,
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
    val qualificationRank: Int,
)

@Serializable
data class KnockoutResultEntryResponseModel(
    val rank: Int,
    val participantId: String,
    val firstName: String,
    val lastName: String,
    val startNumber: Int,
    val club: String? = null,
)
