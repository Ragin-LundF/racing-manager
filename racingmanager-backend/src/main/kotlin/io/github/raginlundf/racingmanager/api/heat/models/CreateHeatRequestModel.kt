package io.github.raginlundf.racingmanager.api.heat.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateHeatRequestModel(
    val participantIds: List<String>,
)
