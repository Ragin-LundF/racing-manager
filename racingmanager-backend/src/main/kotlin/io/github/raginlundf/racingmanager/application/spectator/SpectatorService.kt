package io.github.raginlundf.racingmanager.application.spectator

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.application.qualification.QualificationRankingCalculator
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchEntity
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutTournamentEntity
import io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity
import io.github.raginlundf.racingmanager.domain.participant.ParticipantStatus
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking
import io.github.raginlundf.racingmanager.domain.qualification.QualificationStatus
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.KnockoutRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.QualificationRepository
import java.util.UUID

class SpectatorService(
    private val eventRepository: EventRepository,
    private val heatRepository: HeatRepository,
    private val participantRepository: ParticipantRepository,
    private val qualificationRepository: QualificationRepository,
    private val knockoutRepository: KnockoutRepository,
) {
    private companion object {
        const val MAX_UPCOMING_HEATS = 5
    }

    fun getSnapshot(eventId: UUID): SpectatorSnapshot? {
        val event = eventRepository.findById(eventId) ?: return null

        val allHeats = heatRepository.findByEventId(eventId)
        val qualification = qualificationRepository.findByEventId(eventId)
        val knockout = knockoutRepository.findByEventId(eventId)

        val activeParticipants = participantRepository.findByEventId(eventId)
            .filter { it.status == ParticipantStatus.ACTIVE }

        val currentHeat = findCurrentHeat(allHeats)
        val upcomingHeats = findUpcomingHeats(allHeats, currentHeat?.id)
        val rankings = if (qualification != null) {
            QualificationRankingCalculator.calculate(activeParticipants, allHeats.filter { it.round == 1 })
        } else {
            emptyList()
        }
        val knockoutState = buildKnockoutState(knockout)
        val knockoutStandings = if (knockout != null) {
            buildKnockoutStandings(activeParticipants, allHeats, rankings, knockout)
        } else {
            emptyList()
        }

        return SpectatorSnapshot(
            event = event,
            currentHeat = currentHeat,
            upcomingHeats = upcomingHeats,
            qualificationRankings = rankings,
            qualificationStatus = qualification?.status?.name,
            knockout = knockoutState,
            knockoutStandings = knockoutStandings,
        )
    }

    /**
     * One row per active participant for the knockout spectator view: best qualification time (from
     * [rankings]), best knockout time (fastest FINISHED measurement across round-2 heats), and the
     * current knockout state derived from the participant's latest match.
     *
     * Ordered as a live placement board: participants who reached a further round rank first, then
     * within a tier the still-advancing (not eliminated) ahead of the eliminated, then fastest time.
     * Each row carries its 1-based [place] and a [racing] flag for the pair in the in-progress match.
     */
    private fun buildKnockoutStandings(
        participants: List<ParticipantEntity>,
        allHeats: List<HeatEntity>,
        rankings: List<QualificationRanking>,
        tournament: KnockoutTournamentEntity,
    ): List<SpectatorParticipantStanding> {
        val koBest = QualificationRankingCalculator
            .calculate(participants, allHeats.filter { it.round == 2 })
            .associate { it.participantId to it.bestTimeNanos }
        val qualBest = rankings.associate { it.participantId to it.bestTimeNanos }
        val matches = knockoutRepository.findMatchesByTournamentId(tournament.id)

        fun matchesFor(id: UUID) = matches.filter { it.participant1Id == id || it.participant2Id == id }
        val furthestRound = participants.associate { p -> p.id to (matchesFor(p.id).maxOfOrNull { it.roundNumber } ?: 0) }
        // Accepted results only: a match becomes COMPLETED when its heat result is accepted.
        val eliminated = participants.filter { p ->
            matchesFor(p.id).any { it.status == KnockoutMatchStatus.COMPLETED && it.winnerId != null && it.winnerId != p.id }
        }.map { it.id }.toSet()
        val racing = matches.filter { it.status == KnockoutMatchStatus.IN_PROGRESS }
            .flatMap { listOfNotNull(it.participant1Id, it.participant2Id) }
            .toSet()
        fun timeKey(id: UUID) = koBest[id] ?: qualBest[id]

        return participants
            .sortedWith(
                compareByDescending<ParticipantEntity> { furthestRound[it.id] ?: 0 }
                    .thenBy { if (eliminated.contains(it.id)) 1 else 0 }
                    .thenBy { timeKey(it.id) == null }
                    .thenBy { timeKey(it.id) ?: Long.MAX_VALUE },
            )
            .mapIndexed { index, p ->
                SpectatorParticipantStanding(
                    participantId = p.id,
                    startNumber = p.startNumber,
                    firstName = p.firstName,
                    lastName = p.lastName,
                    bestQualificationTimeNanos = qualBest[p.id],
                    bestKnockoutTimeNanos = koBest[p.id],
                    state = knockoutStateFor(p.id, matches),
                    place = index + 1,
                    racing = racing.contains(p.id),
                )
            }
    }

    private fun knockoutStateFor(participantId: UUID, matches: List<KnockoutMatchEntity>): String {
        // Base the badge on the latest *completed* match; a pending future match the participant has
        // advanced into (opponent still TBD) would otherwise look like a bye.
        val latest = matches
            .filter {
                (it.participant1Id == participantId || it.participant2Id == participantId) &&
                    it.status == KnockoutMatchStatus.COMPLETED
            }
            .maxByOrNull { it.roundNumber }
            ?: return "ACTIVE"
        return when {
            latest.participant2Id == null && latest.participant1Id == participantId -> "BYE"
            latest.winnerId == participantId -> "WON"
            else -> "OUT"
        }
    }

    private fun findCurrentHeat(heats: List<HeatEntity>): HeatEntity? {
        val active = heats.filter { it.status == HeatStatus.ARMED || it.status == HeatStatus.STARTED }
        if (active.isNotEmpty()) return active.first()

        val latestFinished = heats
            .filter {
                it.status == HeatStatus.FINISHED ||
                    it.status == HeatStatus.TIMEOUT ||
                    it.status == HeatStatus.ACCEPTED
            }
            .maxByOrNull { it.finishedAt ?: it.createdAt }
        return latestFinished
    }

    private fun findUpcomingHeats(allHeats: List<HeatEntity>, excludeId: UUID?): List<HeatEntity> {
        return allHeats
            .filter { it.status == HeatStatus.PLANNED && (excludeId == null || it.id != excludeId) }
            .sortedBy { it.heatNumber }
            .take(MAX_UPCOMING_HEATS)
    }

    private fun buildKnockoutState(
        tournament: KnockoutTournamentEntity?,
    ): SpectatorKnockoutState? {
        if (tournament == null) return null

        val matches = knockoutRepository.findMatchesByTournamentId(tournament.id)
        val rounds = matches.groupBy { it.roundNumber }.map { (roundNumber, roundMatches) ->
            SpectatorKnockoutRound(
                roundNumber = roundNumber,
                matches = roundMatches.map { it.toSpectatorMatch() },
            )
        }.sortedBy { it.roundNumber }

        return SpectatorKnockoutState(
            status = tournament.status.name,
            pairingMode = tournament.pairingMode.name,
            rounds = rounds,
        )
    }

    private fun KnockoutMatchEntity.toSpectatorMatch(): SpectatorKnockoutMatch {
        return SpectatorKnockoutMatch(
            id = id,
            roundNumber = roundNumber,
            matchNumber = matchNumber,
            participant1Id = participant1Id,
            participant2Id = participant2Id,
            winnerId = winnerId,
            status = status.name,
            isBye = participant2Id == null,
        )
    }
}

