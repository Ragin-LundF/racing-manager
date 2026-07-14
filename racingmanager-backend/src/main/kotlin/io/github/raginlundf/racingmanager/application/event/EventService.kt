package io.github.raginlundf.racingmanager.application.event

import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.event.SyncStatus
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import java.util.UUID
import kotlin.time.Clock

@Suppress("TooManyFunctions")
class EventService(
    private val eventRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val auditRepository: AuditRepository,
) {
    private val clock: Clock = Clock.System

    fun create(
        name: String,
        description: String?,
        settings: EventSettings,
        actorId: UUID,
        tenantId: UUID,
    ): CreateEventResult {
        val now = clock.now()
        val event = EventEntity(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            name = name,
            description = description,
            status = EventStatus.DRAFT,
            settings = settings,
            version = 0L,
            createdBy = actorId,
            createdAt = now,
        )
        eventRepository.insert(event = event)
        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_CREATED",
                targetType = "Event",
                targetId = event.id,
                summary = "Event '${event.name}' created",
                occurredAt = now,
            ),
        )
        return CreateEventResult.Success(event = event)
    }

    fun findById(id: UUID): EventEntity? {
        return eventRepository.findById(id = id)
    }

    fun findAll(): List<EventEntity> {
        return eventRepository.findAll()
    }

    fun findAllForTenant(tenantId: UUID): List<EventEntity> {
        return eventRepository.findAllForTenant(tenantId = tenantId)
    }

    @Suppress("LongParameterList")
    fun update(
        id: UUID,
        name: String,
        description: String?,
        settings: EventSettings,
        expectedVersion: Long,
        actorId: UUID,
    ): UpdateEventResult {
        val existing = eventRepository.findById(id = id)
            ?: return UpdateEventResult.NotFound

        if (existing.lockedForSync) {
            return UpdateEventResult.Locked
        }

        if (existing.status != EventStatus.DRAFT) {
            return UpdateEventResult.CannotModifyActiveEvent
        }

        if (existing.version != expectedVersion) {
            return UpdateEventResult.Conflict(expected = expectedVersion, actual = existing.version)
        }

        val now = clock.now()
        val updated = existing.copy(
            name = name,
            description = description,
            settings = settings,
            version = existing.version + 1,
            updatedAt = now,
        )

        val success = eventRepository.update(event = updated)
        if (!success) {
            val refreshed = eventRepository.findById(id = id)
            return UpdateEventResult.Conflict(
                expected = expectedVersion,
                actual = refreshed?.version ?: -1,
            )
        }

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_UPDATED",
                targetType = "Event",
                targetId = id,
                summary = "Event '${updated.name}' updated",
                occurredAt = now,
            ),
        )
        return UpdateEventResult.Success(event = updated)
    }

    fun delete(id: UUID, actorId: UUID): DeleteEventResult {
        val existing = eventRepository.findById(id = id)
            ?: return DeleteEventResult.NotFound

        // Remove child participants (and their vehicles) first, then the event.
        // ponytail: qualification/heat rows keyed by this event become orphaned but
        // are unreachable once the event is gone — cascade them here if that matters.
        participantRepository.deleteByEventId(eventId = id)
        eventRepository.delete(id = id)

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_DELETED",
                targetType = "Event",
                targetId = id,
                summary = "Event '${existing.name}' deleted",
                occurredAt = clock.now(),
            ),
        )
        return DeleteEventResult.Success
    }

    fun activate(id: UUID, expectedVersion: Long, actorId: UUID): ActivateEventResult {
        val existing = eventRepository.findById(id = id)
            ?: return ActivateEventResult.NotFound

        if (existing.status != EventStatus.DRAFT) {
            return ActivateEventResult.InvalidStatus(current = existing.status)
        }

        if (existing.version != expectedVersion) {
            return ActivateEventResult.Conflict(expected = expectedVersion, actual = existing.version)
        }

        val now = clock.now()
        val activated = existing.copy(
            status = EventStatus.ACTIVE,
            version = existing.version + 1,
            updatedAt = now,
            activatedAt = now,
            // An imported event becomes "actively being run locally" the moment
            // it's activated (design §I.4) — organically-created events have no
            // sync status and this is a no-op for them.
            syncStatus = if (existing.syncStatus == SyncStatus.IMPORTED) {
                SyncStatus.LOCAL_ACTIVE
            } else {
                existing.syncStatus
            }
        )

        val success = eventRepository.update(event = activated)
        if (!success) {
            val refreshed = eventRepository.findById(id = id)
            return ActivateEventResult.Conflict(
                expected = expectedVersion,
                actual = refreshed?.version ?: -1,
            )
        }

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_ACTIVATED",
                targetType = "Event",
                targetId = id,
                summary = "Event '${activated.name}' activated",
                occurredAt = now,
            ),
        )
        return ActivateEventResult.Success(event = activated)
    }

    fun archive(id: UUID, actorId: UUID): ArchiveEventResult {
        val existing = eventRepository.findById(id = id)
            ?: return ArchiveEventResult.NotFound

        if (existing.status != EventStatus.ACTIVE) {
            return ArchiveEventResult.InvalidStatus(current = existing.status)
        }

        val now = clock.now()
        val archived = existing.copy(
            status = EventStatus.ARCHIVED,
            version = existing.version + 1,
            updatedAt = now,
        )

        eventRepository.update(event = archived)

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_ARCHIVED",
                targetType = "Event",
                targetId = id,
                summary = "Event '${archived.name}' archived",
                occurredAt = now,
            ),
        )
        return ArchiveEventResult.Success(event = archived)
    }

    fun reactivate(id: UUID, actorId: UUID): ReactivateEventResult {
        val existing = eventRepository.findById(id = id)
            ?: return ReactivateEventResult.NotFound

        if (existing.status != EventStatus.ARCHIVED) {
            return ReactivateEventResult.InvalidStatus(current = existing.status)
        }

        val now = clock.now()
        val reactivated = existing.copy(
            status = EventStatus.ACTIVE,
            version = existing.version + 1,
            updatedAt = now,
        )

        eventRepository.update(event = reactivated)

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_REACTIVATED",
                targetType = "Event",
                targetId = id,
                summary = "Event '${reactivated.name}' reactivated",
                occurredAt = now,
            ),
        )
        return ReactivateEventResult.Success(event = reactivated)
    }

    fun completeEvent(eventId: UUID, actorId: UUID): CompleteEventResult {
        val event = eventRepository.findById(id = eventId)
            ?: return CompleteEventResult.NotFound

        if (event.status != EventStatus.ACTIVE) {
            return CompleteEventResult.InvalidStatus(status = event.status)
        }

        val now = clock.now()
        val completed = event.copy(
            status = EventStatus.COMPLETED,
            version = event.version + 1,
            updatedAt = now,
        )
        eventRepository.update(event = completed)

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_COMPLETED",
                targetType = "Event",
                targetId = eventId,
                summary = "Event '''" + event.name + "'' marked as completed",
                occurredAt = now,
            ),
        )
        return CompleteEventResult.Success(event = completed)
    }

    fun reopenEvent(eventId: UUID, actorId: UUID): ReopenEventResult {
        val event = eventRepository.findById(id = eventId)
            ?: return ReopenEventResult.NotFound

        if (event.status != EventStatus.COMPLETED) {
            return ReopenEventResult.InvalidStatus(status = event.status)
        }

        val now = clock.now()
        val reopened = event.copy(
            status = EventStatus.ACTIVE,
            version = event.version + 1,
            updatedAt = now,
        )
        eventRepository.update(event = reopened)

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_REOPENED",
                targetType = "Event",
                targetId = eventId,
                summary = "Event '''" + event.name + "'' reopened",
                occurredAt = now,
            ),
        )
        return ReopenEventResult.Success(event = reopened)
    }
}

