package io.github.raginlundf.racingmanager.api.sync.models

import kotlinx.serialization.Serializable

@Serializable
data class PairResponseModel(
    val localInstanceId: String,
    val tenantId: String,
)
