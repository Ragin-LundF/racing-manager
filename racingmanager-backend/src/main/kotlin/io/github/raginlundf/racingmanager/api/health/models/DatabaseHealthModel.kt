package io.github.raginlundf.racingmanager.api.health.models

import kotlinx.serialization.Serializable

@Serializable
data class DatabaseHealthModel(
    val connected: Boolean,
    val pingMs: Long,
)
