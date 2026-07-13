package io.github.raginlundf.racingmanager.api.tenant.models

import kotlinx.serialization.Serializable

@Serializable
data class TenantResponseModel(
    val id: String,
    val slug: String?,
    val displayName: String,
    val status: String,
    val settings: String?,
)
