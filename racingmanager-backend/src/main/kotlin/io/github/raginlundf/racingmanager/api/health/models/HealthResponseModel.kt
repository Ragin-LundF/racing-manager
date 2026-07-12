package io.github.raginlundf.racingmanager.api.health.models

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponseModel(
    val status: String,
    val database: DatabaseHealthModel? = null,
)

@Serializable
data class DatabaseHealthModel(
    val connected: Boolean,
    val pingMs: Long,
)

@Serializable
data class ReadinessResponseModel(
    val status: String,
    val checks: List<ReadinessCheckModel>,
)

@Serializable
data class ReadinessCheckModel(
    val name: String,
    val status: String,
    val error: String? = null,
)