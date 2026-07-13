package io.github.raginlundf.racingmanager.api.participant.models

import kotlinx.serialization.Serializable

@Serializable
data class RandomizeRequestModel(
    val force: Boolean = false,
)
