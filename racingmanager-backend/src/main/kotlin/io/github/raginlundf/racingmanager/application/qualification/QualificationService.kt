package io.github.raginlundf.racingmanager.application.qualification

import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity
import io.github.raginlundf.racingmanager.domain.participant.ParticipantStatus
import io.github.raginlundf.racingmanager.domain.qualification.QualificationEntity
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking
import io.github.raginlundf.racingmanager.domain.qualification.QualificationStatus
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.QualificationRepository
import kotlin.time.Clock
import java.util.UUID

class QualificationService(
    private val qualificationRepository: QualificationRepository,
    private val heatRepository: HeatRepository,
    private val eventRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val auditRepository: AuditRepository,
) {
    private val clock: Clock = Clock.System

    fun findByEventId(eventId: UUID): QualificationEntity? {
        return qualificationRepository.findByEventId(eventId)
    }

    fun getRankings(eventId: UUID): List<QualificationRanking> {
        val qualification = qualificationRepository.findByEventId(eventId) ?: return emptyList()
        val participants = participantRepository.findByEventId(eventId)
            .filter { it.status == ParticipantStatus.ACTIVE }
        val heats = heatRepository.findByEventId(eventId)
            .filter { it.round == 1 }

        return calculateRankings(participants, heats, qualification)
    }

    fun setup(eventId: UUID, numberOfRuns: Int, actorId: UUID): SetupQualificationResult {
        val event = eventRepository.findById(eventId)
            ?: return SetupQualificationResult.EventNotFound

        if (event.status != EventStatus.ACTIVE) {
            return SetupQualificationResult.EventNotActive
        }

        val existing = qualificationRepository.findByEventId(eventId)
        if (existing != null) {
            return SetupQualificationResult.AlreadyExists(existing)
        }

        val participants = participantRepository.findByEventId(eventId)
            .filter { it.status == ParticipantStatus.ACTIVE }

        if (participants.size < 2) {
            return SetupQualificationResult.NotEnoughParticipants
        }

        val seed = clock.now().toEpochMilliseconds()
        val now = clock.now()
        val qualification = QualificationEntity(
            id = UUID.randomUUID(),
            eventId = eventId,
            status = QualificationStatus.PENDING,
            numberOfRuns = numberOfRuns,
            seed = seed,
            createdAt = now,
        )

        qualificationRepository.insert(qualification)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "QUALIFICATION_SETUP",
                targetType = "Qualification",
                targetId = qualification.id,
                summary = "Qualification setup with $numberOfRuns runs, ${participants.size} participants",
                occurredAt = now,
            ),
        )

        return SetupQualificationResult.Success(qualification)
    }

    fun generateSchedule(eventId: UUID, actorId: UUID): GenerateScheduleResult {
        val qualification = qualificationRepository.findByEventId(eventId)
            ?: return GenerateScheduleResult.QualificationNotFound

        if (qualification.status != QualificationStatus.PENDING) {
            return GenerateScheduleResult.InvalidStatus(qualification.status)
        }

        val participants = participantRepository.findByEventId(eventId)
            .filter { it.status == ParticipantStatus.ACTIVE }

        if (participants.size < 2) {
            return GenerateScheduleResult.NotEnoughParticipants
        }

        val existingHeats = heatRepository.findByEventId(eventId)
            .filter { it.round == 1 }

        if (existingHeats.isNotEmpty()) {
            return GenerateScheduleResult.HeatsAlreadyExist
        }

        val now = clock.now()
        val rng = java.util.Random(qualification.seed)
        var heatNumber = 1

        for (run in 1..qualification.numberOfRuns) {
            val shuffled = participants.shuffled(rng)
            val pairs = shuffled.chunked(2)

            for (pair in pairs) {
                val lane1 = pair[0]
                val lane2 = if (pair.size > 1) pair[1] else null

                val lanes = mutableListOf(
                    HeatLaneAssignment(
                        lane = 1,
                        participantId = lane1.id,
                        participantStartNumber = lane1.startNumber,
                        participantFirstName = lane1.firstName,
                        participantLastName = lane1.lastName,
                    ),
                )

                if (lane2 != null) {
                    lanes.add(
                        HeatLaneAssignment(
                            lane = 2,
                            participantId = lane2.id,
                            participantStartNumber = lane2.startNumber,
                            participantFirstName = lane2.firstName,
                            participantLastName = lane2.lastName,
                        ),
                    )
                }

                val heat = HeatEntity(
                    id = UUID.randomUUID(),
                    eventId = eventId,
                    round = 1,
                    heatNumber = heatNumber,
                    status = HeatStatus.PLANNED,
                    lanes = lanes,
                    measurements = emptyList(),
                    createdAt = now,
                )

                heatRepository.insert(heat)
                heatNumber++
            }
        }

        val updated = qualification.copy(
            status = QualificationStatus.SCHEDULED,
            updatedAt = now,
        )
        qualificationRepository.updateStatus(
            id = qualification.id,
            status = QualificationStatus.SCHEDULED,
            updatedAt = now,
        )

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "QUALIFICATION_SCHEDULED",
                targetType = "Qualification",
                targetId = qualification.id,
                summary = "Qualification schedule generated: ${heatNumber - 1} heats, ${qualification.numberOfRuns} runs",
                occurredAt = clock.now(),
            ),
        )

        return GenerateScheduleResult.Success(updated)
    }

    fun getSchedule(eventId: UUID): List<HeatEntity> {
        return heatRepository.findByEventId(eventId)
            .filter { it.round == 1 }
    }

    fun getProgress(eventId: UUID): QualificationProgress {
        val qualification = qualificationRepository.findByEventId(eventId)
        val heats = heatRepository.findByEventId(eventId)
            .filter { it.round == 1 }

        val totalHeats = heats.size
        val completedHeats = heats.count { it.status == HeatStatus.FINISHED || it.status == HeatStatus.TIMEOUT }
        val inProgressHeats = heats.count { it.status == HeatStatus.ARMED || it.status == HeatStatus.STARTED }
        val plannedHeats = heats.count { it.status == HeatStatus.PLANNED }
        val cancelledHeats = heats.count { it.status == HeatStatus.CANCELLED || it.status == HeatStatus.TECHNICAL_ERROR }

        val participants = participantRepository.findByEventId(eventId)
            .filter { it.status == ParticipantStatus.ACTIVE }
        val allMeasurements = heats.flatMap { it.measurements }
        val participantsWithResults = allMeasurements
            .filter { it.outcome == LaneOutcome.FINISHED }
            .map { m -> heats.firstOrNull { h -> h.id == m.heatId } }
            .filterNotNull()
            .flatMap { it.lanes }
            .map { it.participantId }
            .distinct()
            .size

        return QualificationProgress(
            status = qualification?.status ?: QualificationStatus.PENDING,
            totalHeats = totalHeats,
            completedHeats = completedHeats,
            inProgressHeats = inProgressHeats,
            plannedHeats = plannedHeats,
            cancelledHeats = cancelledHeats,
            totalParticipants = participants.size,
            participantsWithResults = participantsWithResults,
        )
    }

    fun finalize(eventId: UUID, actorId: UUID): FinalizeResult {
        val qualification = qualificationRepository.findByEventId(eventId)
            ?: return FinalizeResult.QualificationNotFound

        if (qualification.status != QualificationStatus.SCHEDULED && qualification.status != QualificationStatus.IN_PROGRESS) {
            return FinalizeResult.InvalidStatus(qualification.status)
        }

        val heats = heatRepository.findByEventId(eventId)
            .filter { it.round == 1 }

        val incompleteHeats = heats.filter { h ->
            h.status != HeatStatus.FINISHED &&
            h.status != HeatStatus.TIMEOUT &&
            h.status != HeatStatus.CANCELLED &&
            h.status != HeatStatus.TECHNICAL_ERROR
        }

        if (incompleteHeats.isNotEmpty()) {
            return FinalizeResult.IncompleteHeats(incompleteHeats.size)
        }

        val now = clock.now()
        qualificationRepository.updateStatus(
            id = qualification.id,
            status = QualificationStatus.FINALIZED,
            updatedAt = now,
            finalizedAt = now,
            finalizedBy = actorId,
        )

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "QUALIFICATION_FINALIZED",
                targetType = "Qualification",
                targetId = qualification.id,
                summary = "Qualification finalized",
                occurredAt = clock.now(),
            ),
        )

        return FinalizeResult.Success
    }

    fun reopen(eventId: UUID, actorId: UUID): ReopenResult {
        val qualification = qualificationRepository.findByEventId(eventId)
            ?: return ReopenResult.QualificationNotFound

        if (qualification.status != QualificationStatus.FINALIZED) {
            return ReopenResult.InvalidStatus(qualification.status)
        }

        val now = clock.now()
        qualificationRepository.updateStatus(
            id = qualification.id,
            status = QualificationStatus.IN_PROGRESS,
            updatedAt = now,
            finalizedAt = null,
            finalizedBy = null,
        )

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "QUALIFICATION_REOPENED",
                targetType = "Qualification",
                targetId = qualification.id,
                summary = "Qualification reopened",
                occurredAt = clock.now(),
            ),
        )

        return ReopenResult.Success
    }

    private fun calculateRankings(
        participants: List<ParticipantEntity>,
        heats: List<HeatEntity>,
        qualification: QualificationEntity,
    ): List<QualificationRanking> {
        return QualificationRankingCalculator.calculate(
            participants = participants,
            heats = heats,
            qualification = qualification,
        )
    }
}

