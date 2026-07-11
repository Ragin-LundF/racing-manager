package io.github.raginlundf.racingmanager.application.event

import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import kotlin.time.Clock
import java.util.UUID

class EventService(
    private val eventRepository: EventRepository,
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

sealed interface ArchiveEventResult {
    data class Success(val event: EventEntity) : ArchiveEventResult
    data object NotFound : ArchiveEventResult
    data class InvalidStatus(val current: EventStatus) : ArchiveEventResult
}
