package io.github.raginlundf.racingmanager.domain.tenant

import kotlin.time.Instant
import java.util.UUID

data class TenantEntity(
    val id: UUID,
    val slug: String? = null,
    val displayName: String,
    val status: TenantStatus = TenantStatus.ACTIVE,
    val settings: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
)
