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

        return calculateRankings(participants, heats)
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
        val heatsCreated = scheduleQualificationHeats(
            eventId = eventId,
            participants = participants,
            numberOfRuns = qualification.numberOfRuns,
            rng = rng,
            now = now,
        )

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
                summary = "Qualification schedule generated: $heatsCreated heats, ${qualification.numberOfRuns} runs",
                occurredAt = clock.now(),
            ),
        )

        return GenerateScheduleResult.Success(updated)
    }

    private fun scheduleQualificationHeats(
        eventId: UUID,
        participants: List<ParticipantEntity>,
        numberOfRuns: Int,
        rng: java.util.Random,
        now: kotlin.time.Instant,
    ): Int {
        val pairings = buildQualificationPairings(participants, numberOfRuns, rng)
        var heatNumber = 1
        for (pairing in pairings) {
            val heat = buildQualificationHeat(
                eventId = eventId,
                pair = pairing,
                heatNumber = heatNumber,
                now = now,
            )
            heatRepository.insert(heat)
            heatNumber++
        }
        return heatNumber - 1
    }

    private fun buildQualificationHeat(
        eventId: UUID,
        pair: List<ParticipantEntity>,
        heatNumber: Int,
        now: kotlin.time.Instant,
    ): HeatEntity {
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

        return HeatEntity(
            id = UUID.randomUUID(),
            eventId = eventId,
            round = 1,
            heatNumber = heatNumber,
            status = HeatStatus.PLANNED,
            lanes = lanes,
            measurements = emptyList(),
            createdAt = now,
        )
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
        val completedHeats = heats.count {
            it.status == HeatStatus.FINISHED || it.status == HeatStatus.TIMEOUT || it.status == HeatStatus.ACCEPTED
        }
        val inProgressHeats = heats.count { it.status == HeatStatus.ARMED || it.status == HeatStatus.STARTED }
        val plannedHeats = heats.count { it.status == HeatStatus.PLANNED }
        val cancelledHeats = heats.count {
            it.status == HeatStatus.CANCELLED ||
                it.status == HeatStatus.TECHNICAL_ERROR ||
                it.status == HeatStatus.REJECTED
        }

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

        val status = qualification.status
        if (status != QualificationStatus.SCHEDULED && status != QualificationStatus.IN_PROGRESS) {
            return FinalizeResult.InvalidStatus(status)
        }

        val heats = heatRepository.findByEventId(eventId)
            .filter { it.round == 1 }

        val incompleteHeats = heats.filter { h ->
            h.status != HeatStatus.FINISHED &&
            h.status != HeatStatus.TIMEOUT &&
            h.status != HeatStatus.CANCELLED &&
            h.status != HeatStatus.TECHNICAL_ERROR &&
            h.status != HeatStatus.ACCEPTED &&
            h.status != HeatStatus.REJECTED
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
    ): List<QualificationRanking> {
        return QualificationRankingCalculator.calculate(
            participants = participants,
            heats = heats,
        )
    }
}

/**
 * Builds the heat line-up for [numberOfRuns] qualification runs while minimising the number
 * of "solo" heats (a racer running alone). Uses the round-robin circle method so each racer
 * appears exactly [numberOfRuns] times; racers who sit out a run (the bye, when the field is
 * odd) are paired against each other in extra heats instead of running solo. At most one solo
 * heat remains, and only when both the field size and [numberOfRuns] are odd.
 */
private fun buildQualificationPairings(
    participants: List<ParticipantEntity>,
    numberOfRuns: Int,
    rng: java.util.Random,
): List<List<ParticipantEntity>> {
    val ordered = participants.shuffled(rng)
    // Even the field with a null "bye" sentinel so the circle method pairs everyone up.
    val slots: List<ParticipantEntity?> = if (ordered.size % 2 == 0) ordered else ordered + null
    val size = slots.size

    val pairings = mutableListOf<List<ParticipantEntity>>()
    val byes = mutableListOf<ParticipantEntity>()

    var rotation = slots
    repeat(numberOfRuns) {
        for (i in 0 until size / 2) {
            val first = rotation[i]
            val second = rotation[size - 1 - i]
            when {
                first != null && second != null -> pairings.add(listOf(first, second))
                first != null -> byes.add(first)
                second != null -> byes.add(second)
            }
        }
        rotation = rotateForNextRound(rotation)
    }

    addByePairings(pairings, byes)
    return pairings
}

// Standard circle-method rotation: keep the first slot fixed and rotate the rest by one.
private fun rotateForNextRound(rotation: List<ParticipantEntity?>): List<ParticipantEntity?> {
    if (rotation.size <= 2) return rotation
    val tail = rotation.subList(1, rotation.size)
    return listOf(rotation.first(), tail.last()) + tail.dropLast(1)
}

// Pair racers who sat out (byes) against each other so they get a timed run rather than
// running solo. A single leftover bye (odd number of byes) becomes the one unavoidable solo.
private fun addByePairings(
    pairings: MutableList<List<ParticipantEntity>>,
    byes: List<ParticipantEntity>,
) {
    var i = 0
    while (i < byes.size) {
        val first = byes[i]
        val second = byes.getOrNull(i + 1)
        when {
            second == null -> pairings.add(listOf(first))
            second.id == first.id -> {
                // Same racer byed twice (numberOfRuns > field size): keep as separate solos.
                pairings.add(listOf(first))
                pairings.add(listOf(second))
            }
            else -> pairings.add(listOf(first, second))
        }
        i += 2
    }
}

