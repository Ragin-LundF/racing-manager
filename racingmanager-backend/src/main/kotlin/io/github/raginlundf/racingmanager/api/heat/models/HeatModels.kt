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

@Serializable
data class HeatLaneResponseModel(
    val lane: Int,
    val participantId: String,
    val participantStartNumber: Int,
    val participantFirstName: String,
    val participantLastName: String,
)

@Serializable
data class MeasurementResponseModel(
    val id: String,
    val heatId: String,
    val lane: Int,
    val durationNanos: Long,
    val outcome: String,
    val receivedAt: String,
)

@Serializable
data class CreateHeatRequestModel(
    val participantIds: List<String>,
)

@Serializable
data class AddMeasurementRequestModel(
    val lane: Int,
    val durationNanos: Long,
    val outcome: String,
)

@Serializable
data class HeatStateChangeEvent(
    val type: String,
    val heat: HeatResponseModel,
)
