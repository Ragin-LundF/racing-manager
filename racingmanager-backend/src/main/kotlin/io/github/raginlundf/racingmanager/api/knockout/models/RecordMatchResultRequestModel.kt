package io.github.raginlundf.racingmanager.api.knockout.models

import kotlinx.serialization.Serializable

@Serializable
data class RecordMatchResultRequestModel(
    val matchId: String,
    val winnerId: String,
    val heatId: String,
)
