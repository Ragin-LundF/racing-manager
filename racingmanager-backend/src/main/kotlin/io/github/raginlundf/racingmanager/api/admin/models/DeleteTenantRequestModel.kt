package io.github.raginlundf.racingmanager.api.admin.models

import kotlinx.serialization.Serializable

@Serializable
data class DeleteTenantRequestModel(
    /** Must match the target tenant's slug exactly — the explicit confirmation
        gate against deleting a tenant by ID alone (design §7/§12). */
    val confirmSlug: String,
)
