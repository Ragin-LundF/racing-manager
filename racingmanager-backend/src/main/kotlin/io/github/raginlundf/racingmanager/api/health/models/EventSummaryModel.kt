package io.github.raginlundf.racingmanager.api.health.models

import kotlinx.serialization.Serializable

@Serializable
data class EventSummaryModel(
    val total: Int,
    val draft: Int,
    val active: Int,
    val completed: Int,
    val archived: Int,
    val totalParticipants: Int,
    val totalHeats: Int,
)
