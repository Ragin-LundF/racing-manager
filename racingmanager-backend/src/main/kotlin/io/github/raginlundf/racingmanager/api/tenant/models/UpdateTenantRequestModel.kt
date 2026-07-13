package io.github.raginlundf.racingmanager.api.tenant.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateTenantRequestModel(
    val displayName: String,
    val settings: String? = null,
)
