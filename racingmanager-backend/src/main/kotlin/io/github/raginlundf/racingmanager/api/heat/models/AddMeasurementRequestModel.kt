package io.github.raginlundf.racingmanager.api.heat.models

import kotlinx.serialization.Serializable

@Serializable
data class AddMeasurementRequestModel(
    val lane: Int,
    val durationNanos: Long,
    val outcome: String,
)
