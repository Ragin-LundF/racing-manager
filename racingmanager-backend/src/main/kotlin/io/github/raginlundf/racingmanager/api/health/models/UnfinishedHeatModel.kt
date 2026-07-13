package io.github.raginlundf.racingmanager.api.health.models

import kotlinx.serialization.Serializable

@Serializable
data class UnfinishedHeatModel(
    val heatId: String,
    val heatNumber: Int,
    val round: Int,
    val status: String,
    val eventId: String,
    val eventName: String,
)
