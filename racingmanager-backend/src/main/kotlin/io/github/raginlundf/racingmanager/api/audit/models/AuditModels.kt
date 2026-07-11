package io.github.raginlundf.racingmanager.api.audit.models

import kotlinx.serialization.Serializable

@Serializable
data class AuditEntryResponseModel(
    val id: String,
    val actorId: String? = null,
    val action: String,
    val targetType: String? = null,
    val targetId: String? = null,
    val summary: String? = null,
    val details: String? = null,
    val correlationId: String? = null,
    val occurredAt: String,
)

@Serializable
data class AuditQueryRequestModel(
    val action: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    val actorId: String? = null,
    val limit: Int = 100,
    val offset: Int = 0,
)
