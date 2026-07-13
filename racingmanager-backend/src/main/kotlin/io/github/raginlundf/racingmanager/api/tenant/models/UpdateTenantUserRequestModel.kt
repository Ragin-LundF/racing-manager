package io.github.raginlundf.racingmanager.api.tenant.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateTenantUserRequestModel(
    /** `"ADMIN"` or `"DIRECTOR"`; omit to leave the role unchanged. */
    val role: String? = null,
    /** `"ACTIVE"` or `"DISABLED"`; omit to leave the status unchanged. */
    val status: String? = null,
)
