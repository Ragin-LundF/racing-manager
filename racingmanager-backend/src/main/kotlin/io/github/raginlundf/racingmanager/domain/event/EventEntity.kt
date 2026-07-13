package io.github.raginlundf.racingmanager.domain.event

import kotlin.time.Instant
import java.util.UUID

data class EventEntity(
    val id: UUID,
    val tenantId: UUID,
    val name: String,
    val description: String? = null,
    val status: EventStatus = EventStatus.DRAFT,
    val settings: EventSettings = EventSettings(),
    val version: Long = 0L,
    val createdBy: UUID,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val activatedAt: Instant? = null,
    /** Provenance for an event imported from a hosted-→local bootstrap
        package (design §H) — null for an event created directly in its own
        tenant. Never used for access control (that is always [tenantId]);
        purely informational, so the local operator can see where an
        imported event came from. */
    val originTenantId: UUID? = null,
    val originPackageId: UUID? = null,
    /** True while this event is checked out to a local instance for offline
        execution (design §I.3) — hosted-side edits are rejected with
        `423 Locked` until the local instance syncs its results back. */
    val lockedForSync: Boolean = false,
    val syncStatus: SyncStatus? = null,
)
