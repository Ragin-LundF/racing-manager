package io.github.raginlundf.racingmanager.api.sync.models

import kotlinx.serialization.Serializable

@Serializable
data class PairRequestModel(
    val pairingCode: String,
    val localInstanceId: String,
)
