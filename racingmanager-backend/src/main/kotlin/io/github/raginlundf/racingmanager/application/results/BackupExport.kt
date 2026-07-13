package io.github.raginlundf.racingmanager.application.results

data class BackupExport(
    val schemaVersion: Int,
    val exportedAt: String,
    val snapshot: EventResultSnapshot,
)
