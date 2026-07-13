package io.github.raginlundf.racingmanager.api.health.models

import kotlinx.serialization.Serializable

@Serializable
data class ReadinessCheckModel(
    val name: String,
    val status: String,
    val error: String? = null,
)
