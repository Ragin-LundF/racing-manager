package io.github.raginlundf.racingmanager.api.health.models

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticsResponseModel(
    val database: DatabaseHealthModel,
    val events: EventSummaryModel,
    val unfinishedHeats: List<UnfinishedHeatModel>,
    val version: String,
)

@Serializable
data class EventSummaryModel(
    val total: Int,
    val draft: Int,
    val active: Int,
    val completed: Int,
    val archived: Int,
    val totalParticipants: Int,
    val totalHeats: Int,
)

@Serializable
data class UnfinishedHeatModel(
    val heatId: String,
    val heatNumber: Int,
    val round: Int,
    val status: String,
    val eventId: String,
    val eventName: String,
)

@Serializable
data class RecoveryActionResponseModel(
    val heatId: String,
    val action: String,
)