package io.github.raginlundf.racingmanager.domain.sync

import kotlin.time.Instant
import java.util.UUID

/** The hosted-side registry of local instances paired to a tenant
    (design §I.1/§I.5) — [id] is the same stable id the local instance
    generated for itself on first bootstrap-package import (Slice H's
    `LocalInstanceEntity`). A tenant may have many; each belongs to exactly
    one tenant. */
data class PairedInstanceEntity(
    val id: UUID,
    val tenantId: UUID,
    val status: PairedInstanceStatus = PairedInstanceStatus.ACTIVE,
    val pairedAt: Instant,
    val lastSyncAt: Instant? = null,
)
