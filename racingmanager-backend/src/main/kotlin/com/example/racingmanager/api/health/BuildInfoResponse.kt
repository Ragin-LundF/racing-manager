package com.example.racingmanager.api.health

import kotlinx.serialization.Serializable

@Serializable
data class BuildInfoResponse(
    val name: String,
    val version: String,
)
