package io.github.raginlundf.racingmanager.api.qualification.models

import kotlinx.serialization.Serializable

@Serializable
data class MeasurementScheduleResponseModel(
    val id: String,
    val heatId: String,
    val lane: Int,
    val durationNanos: Long,
    val outcome: String,
    val receivedAt: String,
)
