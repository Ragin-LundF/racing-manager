package io.github.raginlundf.racingmanager.api.results.models

import kotlinx.serialization.Serializable

@Serializable
data class BackupResponseModel(
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val event: EventResultSnapshotResponseModel,
)
