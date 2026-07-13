package io.github.raginlundf.racingmanager.application.participant

import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.participant.EventSeedEntity
import io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity
import io.github.raginlundf.racingmanager.domain.participant.ParticipantStatus
import io.github.raginlundf.racingmanager.domain.participant.VehicleEntity
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import kotlin.time.Clock
import java.util.Random
import java.util.UUID

class ParticipantService(
    private val participantRepository: ParticipantRepository,
    private val eventRepository: EventRepository,
    private val auditRepository: AuditRepository,
) {
    private val clock: Clock = Clock.System

    fun findByEventId(eventId: UUID): List<ParticipantEntity> {
        return participantRepository.findByEventId(eventId)
    }

    fun findById(id: UUID): ParticipantEntity? {
        return participantRepository.findById(id)
    }

    fun create(
        eventId: UUID,
        startNumber: Int?,
        firstName: String,
        lastName: String,
        club: String?,
        vehicleName: String?,
        vehicleCategory: String?,
        actorId: UUID,
    ): CreateParticipantResult {
        val event = eventRepository.findById(eventId)
            ?: return CreateParticipantResult.EventNotFound

        if (event.status != EventStatus.ACTIVE) {
            return CreateParticipantResult.EventNotActive
        }

        val resolvedStartNumber = startNumber
            ?: ((participantRepository.maxStartNumberByEventId(eventId) ?: 0) + 1)

        val existing = participantRepository.findByEventIdAndStartNumber(eventId, resolvedStartNumber)
        if (existing != null) {
            return CreateParticipantResult.DuplicateStartNumber(resolvedStartNumber)
        }

        val now = clock.now()
        val participantId = UUID.randomUUID()
        val vehicle = if (vehicleName != null) {
            VehicleEntity(
                id = UUID.randomUUID(),
                participantId = participantId,
                name = vehicleName,
                category = vehicleCategory,
            )
        } else null

        val participant = ParticipantEntity(
            id = participantId,
            eventId = eventId,
            startNumber = resolvedStartNumber,
            firstName = firstName,
            lastName = lastName,
            club = club,
            status = ParticipantStatus.ACTIVE,
            vehicle = vehicle,
            createdAt = now,
        )
        participantRepository.insert(participant)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "PARTICIPANT_CREATED",
                targetType = "Participant",
                targetId = participantId,
                summary = "Participant #$resolvedStartNumber $firstName $lastName created",
                occurredAt = now,
            ),
        )
        return CreateParticipantResult.Success(participant)
    }

    fun update(
        id: UUID,
        startNumber: Int,
        firstName: String,
        lastName: String,
        club: String?,
        actorId: UUID,
    ): UpdateParticipantResult {
        val existing = participantRepository.findById(id)
            ?: return UpdateParticipantResult.NotFound

        val duplicate = participantRepository.findByEventIdAndStartNumber(existing.eventId, startNumber)
        if (duplicate != null && duplicate.id != id) {
            return UpdateParticipantResult.DuplicateStartNumber(startNumber)
        }

        val now = clock.now()
        val updated = existing.copy(
            startNumber = startNumber,
            firstName = firstName,
            lastName = lastName,
            club = club,
            updatedAt = now,
        )
        participantRepository.update(updated)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "PARTICIPANT_UPDATED",
                targetType = "Participant",
                targetId = id,
                summary = "Participant #$startNumber $firstName $lastName updated",
                occurredAt = now,
            ),
        )
        return UpdateParticipantResult.Success(updated)
    }

    fun deactivate(id: UUID, actorId: UUID): ParticipantActionResult {
        val existing = participantRepository.findById(id)
            ?: return ParticipantActionResult.NotFound

        if (existing.status != ParticipantStatus.ACTIVE) {
            return ParticipantActionResult.AlreadyInactive
        }

        val now = clock.now()
        val updated = existing.copy(status = ParticipantStatus.INACTIVE, updatedAt = now)
        participantRepository.update(updated)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "PARTICIPANT_DEACTIVATED",
                targetType = "Participant",
                targetId = id,
                summary = "Participant #${existing.startNumber} deactivated",
                occurredAt = now,
            ),
        )
        return ParticipantActionResult.Success(updated)
    }

    fun reactivate(id: UUID, actorId: UUID): ParticipantActionResult {
        val existing = participantRepository.findById(id)
            ?: return ParticipantActionResult.NotFound

        if (existing.status != ParticipantStatus.INACTIVE) {
            return ParticipantActionResult.AlreadyActive
        }

        val now = clock.now()
        val updated = existing.copy(status = ParticipantStatus.ACTIVE, updatedAt = now)
        participantRepository.update(updated)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "PARTICIPANT_REACTIVATED",
                targetType = "Participant",
                targetId = id,
                summary = "Participant #${existing.startNumber} reactivated",
                occurredAt = now,
            ),
        )
        return ParticipantActionResult.Success(updated)
    }

    fun randomize(eventId: UUID, actorId: UUID, force: Boolean = false): RandomizeResult {
        val event = eventRepository.findById(eventId)
            ?: return RandomizeResult.EventNotFound

        if (event.status != EventStatus.ACTIVE) {
            return RandomizeResult.EventNotActive
        }

        val existingSeed = participantRepository.findSeedByEventId(eventId)
        if (existingSeed != null && !force) {
            return RandomizeResult.AlreadyRandomized(existingSeed)
        }

        val seed = if (existingSeed != null && force) existingSeed.seed else clock.now().toEpochMilliseconds()
        val random = Random(seed)

        val participants = participantRepository.findByEventId(eventId)
            .filter { it.status == ParticipantStatus.ACTIVE }
            .sortedBy { it.id }

        val shuffled = participants.shuffled(random)
        val updates = shuffled.mapIndexed { index, p -> p.id to index }
        participantRepository.updateSortOrders(updates)

        val seedEntity = EventSeedEntity(
            eventId = eventId,
            seed = seed,
            randomizedAt = clock.now(),
            randomizedBy = actorId,
        )
        participantRepository.upsertSeed(seedEntity)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "PARTICIPANTS_RANDOMIZED",
                targetType = "Event",
                targetId = eventId,
                summary = "Participants randomized with seed $seed",
                occurredAt = clock.now(),
            ),
        )
        return RandomizeResult.Success(seed)
    }

    fun importCsv(
        eventId: UUID,
        rows: List<CsvParticipantRow>,
        actorId: UUID,
    ): ImportResult {
        val event = eventRepository.findById(eventId)
            ?: return ImportResult.EventNotFound

        if (event.status != EventStatus.ACTIVE) {
            return ImportResult.EventNotActive
        }

        val errors = mutableListOf<ImportRowError>()
        val created = mutableListOf<ParticipantEntity>()
        val now = clock.now()

        rows.forEachIndexed { index, row ->
            if (row.startNumber == null) {
                errors.add(ImportRowError(index, "Start number is required"))
                return@forEachIndexed
            }
            if (row.firstName.isNullOrBlank()) {
                errors.add(ImportRowError(index, "First name is required"))
                return@forEachIndexed
            }
            if (row.lastName.isNullOrBlank()) {
                errors.add(ImportRowError(index, "Last name is required"))
                return@forEachIndexed
            }

            val existing = participantRepository.findByEventIdAndStartNumber(eventId, row.startNumber)
            if (existing != null) {
                errors.add(ImportRowError(index, "Duplicate start number ${row.startNumber}"))
                return@forEachIndexed
            }

            val participantId = UUID.randomUUID()
            val vehicle = if (row.vehicleName != null) {
                VehicleEntity(
                    id = UUID.randomUUID(),
                    participantId = participantId,
                    name = row.vehicleName,
                    category = row.vehicleCategory,
                )
            } else null

            val participant = ParticipantEntity(
                id = participantId,
                eventId = eventId,
                startNumber = row.startNumber,
                firstName = row.firstName,
                lastName = row.lastName,
                club = row.club,
                status = ParticipantStatus.ACTIVE,
                vehicle = vehicle,
                createdAt = now,
            )
            participantRepository.insert(participant)
            created.add(participant)
        }

        if (created.isNotEmpty()) {
            auditRepository.insert(
                AuditEntryEntity(
                    id = UUID.randomUUID(),
                    actorId = actorId,
                    action = "PARTICIPANTS_IMPORTED",
                    targetType = "Event",
                    targetId = eventId,
                    summary = "${created.size} participants imported, ${errors.size} errors",
                    occurredAt = clock.now(),
                ),
            )
        }

        return ImportResult.Completed(created, errors)
    }
}

