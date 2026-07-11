package com.example.racingmanager.api.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class SessionResponseModel(
    val userId: String,
    val username: String,
    val displayName: String,
    val role: String,
)
