package com.example.racingmanager.api.health

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
)
