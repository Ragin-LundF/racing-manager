package io.github.raginlundf.racingmanager.api.health.models

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponseModel(
    val status: String,
    val database: DatabaseHealthModel? = null,
)
