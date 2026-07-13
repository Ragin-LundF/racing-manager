package io.github.raginlundf.racingmanager.api.knockout.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateHeatForMatchRequestModel(
    val matchId: String,
)
