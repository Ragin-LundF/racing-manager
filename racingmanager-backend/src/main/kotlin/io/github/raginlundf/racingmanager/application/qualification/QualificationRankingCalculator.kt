package io.github.raginlundf.racingmanager.application.qualification

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity
import io.github.raginlundf.racingmanager.domain.qualification.QualificationEntity
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking

object QualificationRankingCalculator {

    fun calculate(
        participants: List<ParticipantEntity>,
        heats: List<HeatEntity>,
        qualification: QualificationEntity,
    ): List<QualificationRanking> {
        val participantResults = participants.map { participant ->
            val participantHeats = heats.filter { heat ->
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
}
