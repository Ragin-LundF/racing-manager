package io.github.raginlundf.racingmanager.api.participant.models

import kotlinx.serialization.Serializable

@Serializable
data class VehicleResponseModel(
    val id: String,
    val name: String,
    val category: String? = null,
)
