package io.github.raginlundf.racingmanager.api.health.models

import kotlinx.serialization.Serializable

@Serializable
data class ReadinessResponseModel(
    val status: String,
    val checks: List<ReadinessCheckModel>,
)
