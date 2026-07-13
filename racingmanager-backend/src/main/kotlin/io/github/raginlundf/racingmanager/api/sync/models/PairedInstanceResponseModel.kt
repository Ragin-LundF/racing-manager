package io.github.raginlundf.racingmanager.api.sync.models

import kotlinx.serialization.Serializable

@Serializable
data class PairedInstanceResponseModel(
    val id: String,
    val status: String,
    val pairedAt: String,
    val lastSyncAt: String? = null,
)
