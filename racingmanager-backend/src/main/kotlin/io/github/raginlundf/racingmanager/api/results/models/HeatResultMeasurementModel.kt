package io.github.raginlundf.racingmanager.api.results.models

import kotlinx.serialization.Serializable

@Serializable
data class HeatResultMeasurementModel(
    val id: String,
    val lane: Int,
    val durationNanos: Long,
    val outcome: String,
    val receivedAt: String,
)
