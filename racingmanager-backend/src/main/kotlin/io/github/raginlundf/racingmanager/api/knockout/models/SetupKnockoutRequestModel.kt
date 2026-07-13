package io.github.raginlundf.racingmanager.api.knockout.models

import kotlinx.serialization.Serializable

@Serializable
data class SetupKnockoutRequestModel(
    val pairingMode: String,
)
