package io.github.raginlundf.racingmanager.api.heat.models

import kotlinx.serialization.Serializable

@Serializable
data class HeatLaneResponseModel(
    val lane: Int,
    val participantId: String,
    val participantStartNumber: Int,
    val participantFirstName: String,
    val participantLastName: String,
)
