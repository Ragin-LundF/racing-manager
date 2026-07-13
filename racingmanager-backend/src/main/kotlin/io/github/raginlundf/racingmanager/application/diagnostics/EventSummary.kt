package io.github.raginlundf.racingmanager.application.diagnostics

data class EventSummary(
    val total: Int,
    val draft: Int,
    val active: Int,
    val completed: Int,
    val archived: Int,
    val totalParticipants: Int,
    val totalHeats: Int,
)
