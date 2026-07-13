package io.github.raginlundf.racingmanager.domain.sync

import kotlin.time.Instant
import java.util.UUID

/** An authoritative, immutable record of results a local instance uploaded
    for an event (design §I.2/§I.3). [resultsJson] is the same JSON shape a
    local instance already produces via `GET .../results/backup` — this
    layer never parses or reconstructs it into relational heat/measurement
    rows (field-level merge is explicitly out of scope for this release); it
    is retained verbatim as the origin-audited record of what the local
    instance reported. */
data class SyncedResultEntity(
    val id: UUID,
    val eventId: UUID,
    val tenantId: UUID,
    val localInstanceId: UUID,
    val resultsJson: String,
    val syncedAt: Instant,
)
