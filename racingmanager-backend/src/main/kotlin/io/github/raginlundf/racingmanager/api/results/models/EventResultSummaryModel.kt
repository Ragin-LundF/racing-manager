package io.github.raginlundf.racingmanager.api.results.models

import kotlinx.serialization.Serializable

@Serializable
data class EventResultSummaryModel(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String,
    val laneType: String,
    val measurementType: String,
    val createdAt: String,
    val activatedAt: String? = null,
    val completedAt: String? = null,
)
