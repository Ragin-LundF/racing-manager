package io.github.raginlundf.racingmanager.application.sync

import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.domain.event.SyncStatus
import io.github.raginlundf.racingmanager.domain.sync.PairedInstanceEntity
import io.github.raginlundf.racingmanager.domain.sync.PairedInstanceStatus
import io.github.raginlundf.racingmanager.domain.sync.PairingCodeEntity
import io.github.raginlundf.racingmanager.domain.sync.SyncedResultEntity
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.PairedInstanceRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.PairingCodeRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SyncedResultRepository
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import java.util.UUID

sealed interface PairingTokenResult {
    data class Success(val code: UUID, val expiresIn: Long) : PairingTokenResult
}

sealed interface PairResult {
    data class Success(val instance: PairedInstanceEntity) : PairResult
    data object InvalidOrExpiredCode : PairResult
}

sealed interface RevokeResult {
    data object Success : RevokeResult
    data object NotFound : RevokeResult
}

sealed interface SyncResultsResult {
    data class Success(val syncedResultId: UUID) : SyncResultsResult
    data object InstanceNotFound : SyncResultsResult
    data object InstanceRevoked : SyncResultsResult
    data object EventNotFound : SyncResultsResult
    data object EventNotLocked : SyncResultsResult
}

/** Hosted-side pairing and results upload-back (design §I.1–§I.5). Both the
    pairing code and the paired-instance registry are the only durable state
    this slice adds beyond what Slice H already introduced — there is
    deliberately no attempt here to reconstruct heat/measurement rows from an
    uploaded results snapshot (field-level merge is explicitly out of scope
    for this release, per the design doc); the snapshot is retained verbatim
    as the origin-audited authoritative record. */
class SyncService(
    private val pairingCodeRepository: PairingCodeRepository,
    private val pairedInstanceRepository: PairedInstanceRepository,
    private val syncedResultRepository: SyncedResultRepository,
    private val eventRepository: EventRepository,
    private val auditRepository: AuditRepository,
    private val pairingCodeTtl: Duration = 15.minutes,
) {
    private val clock = Clock.System

    fun issuePairingToken(tenantId: UUID): PairingTokenResult {
        val now = clock.now()
        val code = UUID.randomUUID()
        pairingCodeRepository.insert(PairingCodeEntity(id = code, tenantId = tenantId, expiresAt = now.plus(pairingCodeTtl)))
        return PairingTokenResult.Success(code, pairingCodeTtl.inWholeSeconds)
    }

    /** [localInstanceId] is the stable id the local instance generated for
        itself on first bootstrap-package import (Slice H) — pairing just
        registers that id against the tenant that issued the code, it never
        creates or requires a copied admin password. */
    fun pair(pairingCode: UUID, localInstanceId: UUID): PairResult {
        val now = clock.now()
        val code = pairingCodeRepository.consume(pairingCode, now) ?: return PairResult.InvalidOrExpiredCode

        val existing = pairedInstanceRepository.findById(localInstanceId)
        val instance = if (existing != null) {
            pairedInstanceRepository.updateStatus(localInstanceId, PairedInstanceStatus.ACTIVE)
            existing.copy(status = PairedInstanceStatus.ACTIVE)
        } else {
            val created = PairedInstanceEntity(id = localInstanceId, tenantId = code.tenantId, pairedAt = now)
            pairedInstanceRepository.insert(created)
            created
        }
        return PairResult.Success(instance)
    }

    fun listInstances(tenantId: UUID): List<PairedInstanceEntity> = pairedInstanceRepository.findAllForTenant(tenantId)

    fun revoke(tenantId: UUID, instanceId: UUID): RevokeResult {
        val instance = pairedInstanceRepository.findById(instanceId)
        if (instance == null || instance.tenantId != tenantId) return RevokeResult.NotFound
        pairedInstanceRepository.updateStatus(instanceId, PairedInstanceStatus.REVOKED)
        return RevokeResult.Success
    }

    /** Applies an uploaded results snapshot for an event that was checked out
        to [instanceId] (design §I.2/§I.3): stores the snapshot, unlocks the
        hosted event, marks it [SyncStatus.SYNCED], and records [instanceId]'s
        `lastSyncAt`. Rejects an event that was never checked out — there is
        nothing to reconcile against, and it is not this instance's event to
        sync. */
    fun syncResults(tenantId: UUID, instanceId: UUID, eventId: UUID, resultsJson: String, actorId: UUID): SyncResultsResult {
        val instance = pairedInstanceRepository.findById(instanceId)
        if (instance == null || instance.tenantId != tenantId) return SyncResultsResult.InstanceNotFound
        if (instance.status != PairedInstanceStatus.ACTIVE) return SyncResultsResult.InstanceRevoked

        val event = eventRepository.findByIdForTenant(eventId, tenantId) ?: return SyncResultsResult.EventNotFound
        if (!event.lockedForSync) return SyncResultsResult.EventNotLocked

        val now = clock.now()
        val syncedResultId = UUID.randomUUID()
        syncedResultRepository.insert(
            SyncedResultEntity(id = syncedResultId, eventId = eventId, tenantId = tenantId, localInstanceId = instanceId, resultsJson = resultsJson, syncedAt = now),
        )
        eventRepository.update(event.copy(lockedForSync = false, syncStatus = SyncStatus.SYNCED, version = event.version + 1, updatedAt = now))
        pairedInstanceRepository.updateLastSyncAt(instanceId, now)
        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_RESULTS_SYNCED",
                targetType = "Event",
                targetId = eventId,
                summary = "Results synced from local instance $instanceId",
                occurredAt = now,
            ),
        )
        return SyncResultsResult.Success(syncedResultId)
    }
}
