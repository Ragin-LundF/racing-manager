package io.github.raginlundf.racingmanager.api.results.models

import kotlinx.serialization.Serializable

@Serializable
data class HeatResultEntryModel(
    val id: String,
    val round: Int,
    val heatNumber: Int,
    val status: String,
    val lanes: List<HeatResultLaneModel>,
    val measurements: List<HeatResultMeasurementModel>,
    val startedAt: String? = null,
    val finishedAt: String? = null,
)
