package io.github.raginlundf.racingmanager.api.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponseModel(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tenantId: String,
    val scopes: List<String>,
    val userId: String,
    val username: String,
    val displayName: String,
    val role: String,
)
