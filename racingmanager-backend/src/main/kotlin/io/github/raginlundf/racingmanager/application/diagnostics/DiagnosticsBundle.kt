package io.github.raginlundf.racingmanager.application.diagnostics

data class DiagnosticsBundle(
    val database: DatabaseStatus,
    val events: EventSummary,
    val unfinishedHeats: List<UnfinishedHeat>,
    val version: String,
)
