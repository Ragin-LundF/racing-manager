package io.github.raginlundf.racingmanager.application.spectator

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.application.qualification.QualificationRankingCalculator
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchEntity
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutTournamentEntity
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

        val currentHeat = findCurrentHeat(allHeats)
        val upcomingHeats = findUpcomingHeats(allHeats, currentHeat?.id)
        val rankings = if (qualification != null) calculateRankings(eventId, allHeats) else emptyList()
        val knockoutState = buildKnockoutState(knockout)

        return SpectatorSnapshot(
            event = event,
            currentHeat = currentHeat,
            upcomingHeats = upcomingHeats,
            qualificationRankings = rankings,
            qualificationStatus = qualification?.status?.name,
            knockout = knockoutState,
        )
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

    private fun calculateRankings(
        eventId: UUID,
        heats: List<HeatEntity>,
    ): List<QualificationRanking> {
        val participants = participantRepository.findByEventId(eventId)
            .filter { it.status == ParticipantStatus.ACTIVE }
        val qualHeats = heats.filter { it.round == 1 }
        return QualificationRankingCalculator.calculate(
            participants = participants,
            heats = qualHeats,
        )
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

