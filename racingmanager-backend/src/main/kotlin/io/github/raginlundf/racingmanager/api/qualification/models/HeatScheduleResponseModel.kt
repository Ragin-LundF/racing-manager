package io.github.raginlundf.racingmanager.api.qualification.models

import kotlinx.serialization.Serializable

@Serializable
data class HeatScheduleResponseModel(
    val id: String,
    val eventId: String,
    val round: Int,
    val heatNumber: Int,
    val status: String,
    val lanes: List<HeatLaneScheduleResponseModel>,
    val measurements: List<MeasurementScheduleResponseModel>,
    val createdAt: String,
    val armedAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
)
