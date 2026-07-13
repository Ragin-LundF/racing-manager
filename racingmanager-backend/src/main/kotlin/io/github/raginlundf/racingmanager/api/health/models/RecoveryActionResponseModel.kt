package io.github.raginlundf.racingmanager.api.health.models

import kotlinx.serialization.Serializable

@Serializable
data class RecoveryActionResponseModel(
    val heatId: String,
    val action: String,
)
