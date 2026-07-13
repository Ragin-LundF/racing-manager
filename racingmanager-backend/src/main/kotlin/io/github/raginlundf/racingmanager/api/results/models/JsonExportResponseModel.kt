package io.github.raginlundf.racingmanager.api.results.models

import kotlinx.serialization.Serializable

@Serializable
data class JsonExportResponseModel(
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val event: EventResultSnapshotResponseModel,
)
