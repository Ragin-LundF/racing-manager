package io.github.raginlundf.racingmanager.api.participant.models

import kotlinx.serialization.Serializable

@Serializable
data class RandomizeResponseModel(
    val seed: Long,
    val alreadyRandomized: Boolean = false,
)
