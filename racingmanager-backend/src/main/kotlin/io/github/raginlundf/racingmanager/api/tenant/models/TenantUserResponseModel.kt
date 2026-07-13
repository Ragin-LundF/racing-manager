package io.github.raginlundf.racingmanager.api.tenant.models

import kotlinx.serialization.Serializable

@Serializable
data class TenantUserResponseModel(
    val userId: String,
    val username: String,
    val displayName: String,
    val role: String,
    val status: String,
)
