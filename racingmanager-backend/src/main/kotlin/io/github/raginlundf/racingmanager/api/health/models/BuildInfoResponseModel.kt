package io.github.raginlundf.racingmanager.api.health.models

import kotlinx.serialization.Serializable

@Serializable
data class BuildInfoResponseModel(
    val name: String,
    val version: String,
)
