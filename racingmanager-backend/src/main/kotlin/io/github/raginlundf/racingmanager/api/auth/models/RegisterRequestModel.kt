package io.github.raginlundf.racingmanager.api.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestModel(
    val tenantName: String,
    val tenantSlug: String,
    val username: String,
    val password: String,
    val displayName: String,
)
