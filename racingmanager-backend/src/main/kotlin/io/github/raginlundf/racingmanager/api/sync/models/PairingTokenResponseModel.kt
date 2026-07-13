package io.github.raginlundf.racingmanager.api.sync.models

import kotlinx.serialization.Serializable

@Serializable
data class PairingTokenResponseModel(
    val pairingCode: String,
    val expiresIn: Long,
)
