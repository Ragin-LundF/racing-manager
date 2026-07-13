package io.github.raginlundf.racingmanager.api.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class RegisterResponseModel(
    val tenantId: String,
    val tenantSlug: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val scopes: List<String>,
    val userId: String,
    val username: String,
    val displayName: String,
    val role: String,
)
