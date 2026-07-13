package io.github.raginlundf.racingmanager.api.results.models

import kotlinx.serialization.Serializable

@Serializable
data class RestoreResponseModel(
    val eventId: String,
    val name: String,
    val status: String,
)
