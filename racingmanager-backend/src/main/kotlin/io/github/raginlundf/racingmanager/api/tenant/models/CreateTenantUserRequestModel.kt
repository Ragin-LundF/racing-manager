package io.github.raginlundf.racingmanager.api.tenant.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateTenantUserRequestModel(
    val username: String,
    val password: String,
    val displayName: String,
    /** `"ADMIN"` or `"DIRECTOR"`; defaults to `"DIRECTOR"` (`rm:user`) — a
        normal user can never grant themselves `ADMIN` since this endpoint
        itself requires an existing `rm:admin` token to call. */
    val role: String = "DIRECTOR",
)
