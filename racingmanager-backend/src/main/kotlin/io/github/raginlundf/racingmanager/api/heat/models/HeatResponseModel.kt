package io.github.raginlundf.racingmanager.api.heat.models

import kotlinx.serialization.Serializable

@Serializable
data class HeatResponseModel(
    val id: String,
    val eventId: String,
    val round: Int,
    val heatNumber: Int,
    val status: String,
    val lanes: List<HeatLaneResponseModel>,
    val measurements: List<MeasurementResponseModel>,
    val createdAt: String,
    val armedAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
)
