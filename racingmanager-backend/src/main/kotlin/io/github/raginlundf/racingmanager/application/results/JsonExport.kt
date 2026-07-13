package io.github.raginlundf.racingmanager.application.results

data class JsonExport(
    val schemaVersion: Int,
    val exportedAt: String,
    val snapshot: EventResultSnapshot,
)
