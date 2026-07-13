package io.github.raginlundf.racingmanager.api.sync.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SyncResultsRequestModel(
    val eventId: String,
    val results: JsonElement,
)
