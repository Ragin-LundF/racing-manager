package io.github.raginlundf.racingmanager.api.heat.models

import kotlinx.serialization.Serializable

@Serializable
data class HeatStateChangeEvent(
    val type: String,
    val heat: HeatResponseModel,
)
