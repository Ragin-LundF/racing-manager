package io.github.raginlundf.racingmanager.application.event

import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import kotlin.time.Clock
import java.util.UUID

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
    ): CreateEventResult {
        val now = clock.now()
        val event = EventEntity(
            id = UUID.randomUUID(),
            name = name,
            description = description,
            status = EventStatus.DRAFT,
            settings = settings,
            version = 0L,
            createdBy = actorId,
            createdAt = now,
        )
        eventRepository.insert(event)
        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_CREATED",
                targetType = "Event",
                targetId = event.id,
                summary = "Event '${event.name}' created",
                occurredAt = now,
            ),
        )
        return CreateEventResult.Success(event)
    }

    fun findById(id: UUID): EventEntity? = eventRepository.findById(id)

    fun findAll(): List<EventEntity> = eventRepository.findAll()

    fun update(
        id: UUID,
        name: String,
        description: String?,
        settings: EventSettings,
        expectedVersion: Long,
        actorId: UUID,
    ): UpdateEventResult {
        val existing = eventRepository.findById(id)
            ?: return UpdateEventResult.NotFound

        if (existing.status != EventStatus.DRAFT) {
            return UpdateEventResult.CannotModifyActiveEvent
        }

        if (existing.version != expectedVersion) {
            return UpdateEventResult.Conflict(expectedVersion, existing.version)
        }

        val now = clock.now()
        val updated = existing.copy(
            name = name,
            description = description,
            settings = settings,
            version = existing.version + 1,
            updatedAt = now,
        )

        val success = eventRepository.update(updated)
        if (!success) {
            val refreshed = eventRepository.findById(id)
            return UpdateEventResult.Conflict(
                expected = expectedVersion,
                actual = refreshed?.version ?: -1,
            )
        }

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_UPDATED",
                targetType = "Event",
                targetId = id,
                summary = "Event '${updated.name}' updated",
                occurredAt = now,
            ),
        )
        return UpdateEventResult.Success(updated)
    }

    fun delete(id: UUID, actorId: UUID): DeleteEventResult {
        val existing = eventRepository.findById(id)
            ?: return DeleteEventResult.NotFound

        // Remove child participants (and their vehicles) first, then the event.
        // ponytail: qualification/heat rows keyed by this event become orphaned but
        // are unreachable once the event is gone — cascade them here if that matters.
        participantRepository.deleteByEventId(id)
        eventRepository.delete(id)

        auditRepository.insert(
            AuditEntryEntity(
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
        val existing = eventRepository.findById(id)
            ?: return ActivateEventResult.NotFound

        if (existing.status != EventStatus.DRAFT) {
            return ActivateEventResult.InvalidStatus(existing.status)
        }

        if (existing.version != expectedVersion) {
            return ActivateEventResult.Conflict(expectedVersion, existing.version)
        }

        val now = clock.now()
        val activated = existing.copy(
            status = EventStatus.ACTIVE,
            version = existing.version + 1,
            updatedAt = now,
            activatedAt = now,
        )

        val success = eventRepository.update(activated)
        if (!success) {
            val refreshed = eventRepository.findById(id)
            return ActivateEventResult.Conflict(
                expected = expectedVersion,
                actual = refreshed?.version ?: -1,
            )
        }

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_ACTIVATED",
                targetType = "Event",
                targetId = id,
                summary = "Event '${activated.name}' activated",
                occurredAt = now,
            ),
        )
        return ActivateEventResult.Success(activated)
    }

    fun archive(id: UUID, actorId: UUID): ArchiveEventResult {
        val existing = eventRepository.findById(id)
            ?: return ArchiveEventResult.NotFound

        if (existing.status != EventStatus.ACTIVE) {
            return ArchiveEventResult.InvalidStatus(existing.status)
        }

        val now = clock.now()
        val archived = existing.copy(
            status = EventStatus.ARCHIVED,
            version = existing.version + 1,
            updatedAt = now,
        )

        eventRepository.update(archived)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_ARCHIVED",
                targetType = "Event",
                targetId = id,
                summary = "Event '${archived.name}' archived",
                occurredAt = now,
            ),
        )
        return ArchiveEventResult.Success(archived)
    }

    fun completeEvent(eventId: UUID, actorId: UUID): CompleteEventResult {
        val event = eventRepository.findById(eventId)
            ?: return CompleteEventResult.NotFound

        if (event.status != EventStatus.ACTIVE) {
            return CompleteEventResult.InvalidStatus(event.status)
        }

        val now = clock.now()
        val completed = event.copy(
            status = EventStatus.COMPLETED,
            version = event.version + 1,
            updatedAt = now,
        )
        eventRepository.update(completed)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_COMPLETED",
                targetType = "Event",
                targetId = eventId,
                summary = "Event '''" + event.name + "'' marked as completed",
                occurredAt = now,
            ),
        )
        return CompleteEventResult.Success(completed)
    }

    fun reopenEvent(eventId: UUID, actorId: UUID): ReopenEventResult {
        val event = eventRepository.findById(eventId)
            ?: return ReopenEventResult.NotFound

        if (event.status != EventStatus.COMPLETED) {
            return ReopenEventResult.InvalidStatus(event.status)
        }

        val now = clock.now()
        val reopened = event.copy(
            status = EventStatus.ACTIVE,
            version = event.version + 1,
            updatedAt = now,
        )
        eventRepository.update(reopened)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_REOPENED",
                targetType = "Event",
                targetId = eventId,
                summary = "Event '''" + event.name + "'' reopened",
                occurredAt = now,
            ),
        )
        return ReopenEventResult.Success(reopened)
    }
}

sealed interface CreateEventResult {
    data class Success(val event: EventEntity) : CreateEventResult
}

sealed interface UpdateEventResult {
    data class Success(val event: EventEntity) : UpdateEventResult
    data object NotFound : UpdateEventResult
    data object CannotModifyActiveEvent : UpdateEventResult
    data class Conflict(val expected: Long, val actual: Long) : UpdateEventResult
}

sealed interface ActivateEventResult {
    data class Success(val event: EventEntity) : ActivateEventResult
    data object NotFound : ActivateEventResult
    data class InvalidStatus(val current: EventStatus) : ActivateEventResult
    data class Conflict(val expected: Long, val actual: Long) : ActivateEventResult
}

sealed interface DeleteEventResult {
    data object Success : DeleteEventResult
    data object NotFound : DeleteEventResult
}

sealed interface ArchiveEventResult {
    data class Success(val event: EventEntity) : ArchiveEventResult
    data object NotFound : ArchiveEventResult
    data class InvalidStatus(val current: EventStatus) : ArchiveEventResult
}

sealed interface CompleteEventResult {
    data class Success(val event: EventEntity) : CompleteEventResult
    data object NotFound : CompleteEventResult
    data class InvalidStatus(val status: EventStatus) : CompleteEventResult
}

sealed interface ReopenEventResult {
    data class Success(val event: EventEntity) : ReopenEventResult
    data object NotFound : ReopenEventResult
    data class InvalidStatus(val status: EventStatus) : ReopenEventResult
}
