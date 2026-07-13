package io.github.raginlundf.racingmanager.api.health.models

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticsResponseModel(
    val database: DatabaseHealthModel,
    val events: EventSummaryModel,
    val unfinishedHeats: List<UnfinishedHeatModel>,
    val version: String,
)