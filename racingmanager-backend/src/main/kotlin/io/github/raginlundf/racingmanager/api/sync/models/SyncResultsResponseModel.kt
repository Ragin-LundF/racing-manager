package io.github.raginlundf.racingmanager.api.sync.models

import kotlinx.serialization.Serializable

@Serializable
data class SyncResultsResponseModel(
    val syncedResultId: String,
    val eventId: String,
    val status: String,
)
