package io.github.raginlundf.racingmanager.application.spectator

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchEntity
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutTournamentEntity
import io.github.raginlundf.racingmanager.domain.participant.ParticipantStatus
import io.github.raginlundf.racingmanager.domain.qualification.QualificationEntity
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
    fun getSnapshot(eventId: UUID): SpectatorSnapshot? {
        val event = eventRepository.findById(eventId) ?: return null

        val allHeats = heatRepository.findByEventId(eventId)
        val qualification = qualificationRepository.findByEventId(eventId)
        val knockout = knockoutRepository.findByEventId(eventId)

        val currentHeat = findCurrentHeat(allHeats)
        val upcomingHeats = findUpcomingHeats(allHeats, currentHeat?.id)
        val rankings = if (qualification != null) calculateRankings(eventId, qualification, allHeats) else emptyList()
        val knockoutState = buildKnockoutState(knockout, eventId)

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
            .filter { it.status == HeatStatus.FINISHED || it.status == HeatStatus.TIMEOUT }
            .maxByOrNull { it.finishedAt ?: it.createdAt }
        return latestFinished
    }

    private fun findUpcomingHeats(allHeats: List<HeatEntity>, excludeId: UUID?): List<HeatEntity> {
        return allHeats
            .filter { it.status == HeatStatus.PLANNED && (excludeId == null || it.id != excludeId) }
            .sortedBy { it.heatNumber }
            .take(5)
    }

    private fun calculateRankings(
        eventId: UUID,
        qualification: QualificationEntity,
        heats: List<HeatEntity>,
    ): List<QualificationRanking> {
        val participants = participantRepository.findByEventId(eventId)
            .filter { it.status == ParticipantStatus.ACTIVE }
        val qualHeats = heats.filter { it.round == 1 }

        val participantResults = participants.map { participant ->
            val participantHeats = qualHeats.filter { heat ->
                heat.lanes.any { it.participantId == participant.id }
            }

            val validTimes = participantHeats.flatMap { heat ->
                heat.measurements.filter { m ->
                    val lane = heat.lanes.firstOrNull { it.participantId == participant.id }
                    lane != null && m.lane == lane.lane && m.outcome == LaneOutcome.FINISHED
                }
            }.map { it.durationNanos }

            val dnfCount = participantHeats.flatMap { heat ->
                heat.measurements.filter { m ->
                    val lane = heat.lanes.firstOrNull { it.participantId == participant.id }
                    lane != null && m.lane == lane.lane && m.outcome == LaneOutcome.DNF
                }
            }.size

            QualificationRanking(
                participantId = participant.id,
                startNumber = participant.startNumber,
                firstName = participant.firstName,
                lastName = participant.lastName,
                club = participant.club,
                bestTimeNanos = validTimes.minOrNull(),
                totalTimeNanos = if (validTimes.isNotEmpty()) validTimes.sum() else null,
                completedRuns = validTimes.size,
                dnfCount = dnfCount,
                rank = 0,
            )
        }

        val sorted = participantResults.sortedWith(
            compareBy<QualificationRanking> { it.bestTimeNanos != null }
                .thenBy { it.bestTimeNanos }
                .thenBy { it.totalTimeNanos },
        )

        var currentRank = 1
        var previousBest: Long? = null
        return sorted.mapIndexed { index, ranking ->
            val rank = if (index == 0) {
                currentRank
            } else if (ranking.bestTimeNanos != null && previousBest != null && ranking.bestTimeNanos == previousBest) {
                currentRank
            } else {
                currentRank = index + 1
                currentRank
            }
            previousBest = ranking.bestTimeNanos
            ranking.copy(rank = rank)
        }
    }

    private fun buildKnockoutState(
        tournament: KnockoutTournamentEntity?,
        eventId: UUID,
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

    private fun KnockoutMatchEntity.toSpectatorMatch() = SpectatorKnockoutMatch(
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

data class SpectatorSnapshot(
    val event: EventEntity,
    val currentHeat: HeatEntity?,
    val upcomingHeats: List<HeatEntity>,
    val qualificationRankings: List<QualificationRanking>,
    val qualificationStatus: String?,
    val knockout: SpectatorKnockoutState?,
)

data class SpectatorKnockoutState(
    val status: String,
    val pairingMode: String,
    val rounds: List<SpectatorKnockoutRound>,
)

data class SpectatorKnockoutRound(
    val roundNumber: Int,
    val matches: List<SpectatorKnockoutMatch>,
)

data class SpectatorKnockoutMatch(
    val id: UUID,
    val roundNumber: Int,
    val matchNumber: Int,
    val participant1Id: UUID?,
    val participant2Id: UUID?,
    val winnerId: UUID?,
    val status: String,
    val isBye: Boolean,
)
