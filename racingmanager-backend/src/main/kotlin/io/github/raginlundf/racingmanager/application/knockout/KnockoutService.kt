package io.github.raginlundf.racingmanager.application.knockout

import io.github.raginlundf.racingmanager.application.qualification.QualificationRankingCalculator
import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchEntity
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutRankedParticipant
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutTournamentEntity
import io.github.raginlundf.racingmanager.domain.knockout.PairingMode
import io.github.raginlundf.racingmanager.domain.participant.ParticipantStatus
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking
import io.github.raginlundf.racingmanager.domain.qualification.QualificationStatus
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.KnockoutRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.QualificationRepository
import kotlin.time.Clock
import java.util.UUID

class KnockoutService(
    private val knockoutRepository: KnockoutRepository,
    private val heatRepository: HeatRepository,
    private val eventRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val qualificationRepository: QualificationRepository,
    private val auditRepository: AuditRepository,
) {
    private val clock: Clock = Clock.System

    fun findByEventId(eventId: UUID): KnockoutTournamentEntity? {
        return knockoutRepository.findByEventId(eventId)
    }

    fun getMatches(eventId: UUID): List<KnockoutMatchEntity> {
        val tournament = knockoutRepository.findByEventId(eventId) ?: return emptyList()
        return knockoutRepository.findMatchesByTournamentId(tournament.id)
    }

    fun setup(eventId: UUID, pairingMode: PairingMode, actorId: UUID): SetupKnockoutResult {
        val event = eventRepository.findById(eventId)
            ?: return SetupKnockoutResult.EventNotFound

        if (event.status != EventStatus.ACTIVE) {
            return SetupKnockoutResult.EventNotActive
        }

        val existing = knockoutRepository.findByEventId(eventId)
        if (existing != null) {
            return SetupKnockoutResult.AlreadyExists(existing)
        }

        val qualification = qualificationRepository.findByEventId(eventId)
        if (qualification == null || qualification.status != QualificationStatus.FINALIZED) {
            return SetupKnockoutResult.QualificationNotFinalized
        }

        val rankings = getQualificationRankings(eventId)
        if (rankings.size < 2) {
            return SetupKnockoutResult.NotEnoughParticipants
        }

        val now = clock.now()
        val tournament = KnockoutTournamentEntity(
            id = UUID.randomUUID(),
            eventId = eventId,
            status = KnockoutStatus.PAIRING,
            pairingMode = pairingMode,
            qualificationId = qualification.id,
            createdAt = now,
        )

        knockoutRepository.insert(tournament)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "KNOCKOUT_SETUP",
                targetType = "KnockoutTournament",
                targetId = tournament.id,
                summary = "Knockout setup with pairingMode=$pairingMode, ${rankings.size} participants",
                occurredAt = now,
            ),
        )

        return SetupKnockoutResult.Success(tournament)
    }

    fun generatePairings(eventId: UUID, actorId: UUID): GeneratePairingsResult {
        val tournament = knockoutRepository.findByEventId(eventId)
            ?: return GeneratePairingsResult.TournamentNotFound

        if (tournament.status != KnockoutStatus.PAIRING) {
            return GeneratePairingsResult.InvalidStatus(tournament.status)
        }

        val existingMatches = knockoutRepository.findMatchesByTournamentId(tournament.id)
        if (existingMatches.isNotEmpty()) {
            return GeneratePairingsResult.PairingsAlreadyExist
        }

        val rankings = getQualificationRankings(eventId)
        if (rankings.size < 2) {
            return GeneratePairingsResult.NotEnoughParticipants
        }

        val participants = rankings.map { r ->
            KnockoutRankedParticipant(
                participantId = r.participantId,
                startNumber = r.startNumber,
                firstName = r.firstName,
                lastName = r.lastName,
                club = r.club,
                qualificationRank = r.rank,
            )
        }

        val matches = createPairings(tournament.id, participants, tournament.pairingMode)
        val now = clock.now()

        for (match in matches) {
            knockoutRepository.insertMatch(match.copy(createdAt = now))
        }

        knockoutRepository.updateStatus(
            id = tournament.id,
            status = KnockoutStatus.IN_PROGRESS,
            updatedAt = now,
        )

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "KNOCKOUT_PAIRINGS_GENERATED",
                targetType = "KnockoutTournament",
                targetId = tournament.id,
                summary = "Generated ${matches.size} pairings using ${tournament.pairingMode}",
                occurredAt = clock.now(),
            ),
        )

        return GeneratePairingsResult.Success(tournament.copy(status = KnockoutStatus.IN_PROGRESS, updatedAt = now))
    }

    fun getQualifiedParticipants(eventId: UUID): List<QualificationRanking> {
        return getQualificationRankings(eventId)
    }

    fun setManualPairings(eventId: UUID, pairings: List<Pair<UUID, UUID?>>, actorId: UUID): SetManualPairingsResult {
        val tournament = knockoutRepository.findByEventId(eventId)
            ?: return SetManualPairingsResult.TournamentNotFound

        if (tournament.status != KnockoutStatus.PAIRING) {
            return SetManualPairingsResult.InvalidStatus(tournament.status)
        }

        if (tournament.pairingMode != PairingMode.MANUAL) {
            return SetManualPairingsResult.WrongPairingMode
        }

        val existingMatches = knockoutRepository.findMatchesByTournamentId(tournament.id)
        if (existingMatches.isNotEmpty()) {
            return SetManualPairingsResult.PairingsAlreadyExist
        }

        if (pairings.size < 1) {
            return SetManualPairingsResult.NotEnoughParticipants
        }

        val now = clock.now()
        val matches = buildManualFirstRoundMatches(
            tournamentId = tournament.id,
            pairings = pairings,
            createdAt = now,
        ).toMutableList()

        for (match in matches) {
            knockoutRepository.insertMatch(match)
        }

        val subsequentRounds = generateSubsequentRounds(
            tournamentId = tournament.id,
            firstRoundSize = matches.size,
            createdAt = now,
        )
        for (m in subsequentRounds) {
            knockoutRepository.insertMatch(m)
        }
        matches.addAll(subsequentRounds)

        knockoutRepository.updateStatus(
            id = tournament.id,
            status = KnockoutStatus.IN_PROGRESS,
            updatedAt = now,
        )

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "KNOCKOUT_MANUAL_PAIRINGS",
                targetType = "KnockoutTournament",
                targetId = tournament.id,
                summary = "Manual pairings set: ${matches.size} total matches",
                occurredAt = clock.now(),
            ),
        )

        return SetManualPairingsResult.Success(tournament.copy(status = KnockoutStatus.IN_PROGRESS, updatedAt = now))
    }

    private fun buildManualFirstRoundMatches(
        tournamentId: UUID,
        pairings: List<Pair<UUID, UUID?>>,
        createdAt: kotlin.time.Instant,
    ): List<KnockoutMatchEntity> {
        return pairings.mapIndexed { index, pairing ->
            val (p1, p2) = pairing
            val isBye = p2 == null
            KnockoutMatchEntity(
                id = UUID.randomUUID(),
                tournamentId = tournamentId,
                roundNumber = 1,
                matchNumber = index + 1,
                participant1Id = p1,
                participant2Id = p2,
                status = if (isBye) KnockoutMatchStatus.COMPLETED else KnockoutMatchStatus.PLANNED,
                winnerId = if (isBye) p1 else null,
                createdAt = createdAt,
            )
        }
    }

    fun createHeatForMatch(eventId: UUID, matchId: UUID, actorId: UUID): CreateHeatForMatchResult {
        val tournament = knockoutRepository.findByEventId(eventId)
            ?: return CreateHeatForMatchResult.TournamentNotFound

        val matches = knockoutRepository.findMatchesByTournamentId(tournament.id)
        val match = matches.find { it.id == matchId }
            ?: return CreateHeatForMatchResult.MatchNotFound

        if (match.status != KnockoutMatchStatus.PLANNED) {
            return CreateHeatForMatchResult.MatchAlreadyCompleted
        }

        val participant1Id = match.participant1Id
        val participant2Id = match.participant2Id

        if (participant1Id == null) {
            return CreateHeatForMatchResult.MissingParticipants
        }

        val existingHeats = heatRepository.findByEventId(eventId)
        val heatNumber = existingHeats.size + 1

        val now = clock.now()
        val lanes = buildKnockoutHeatLanes(participant1Id = participant1Id, participant2Id = participant2Id)

        val heat = HeatEntity(
            id = UUID.randomUUID(),
            eventId = eventId,
            round = 2,
            heatNumber = heatNumber,
            status = HeatStatus.PLANNED,
            lanes = lanes,
            measurements = emptyList(),
            createdAt = now,
        )

        heatRepository.insert(heat)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "KNOCKOUT_HEAT_CREATED",
                targetType = "KnockoutMatch",
                targetId = matchId,
                summary = "Heat created for knockout match round=${match.roundNumber} match=${match.matchNumber}",
                occurredAt = now,
            ),
        )

        return CreateHeatForMatchResult.Success(heat)
    }

    private fun buildKnockoutHeatLanes(participant1Id: UUID, participant2Id: UUID?): List<HeatLaneAssignment> {
        val lanes = mutableListOf(
            HeatLaneAssignment(
                lane = 1,
                participantId = participant1Id,
                participantStartNumber = 0,
                participantFirstName = "",
                participantLastName = "",
            ),
        )

        if (participant2Id != null) {
            lanes.add(
                HeatLaneAssignment(
                    lane = 2,
                    participantId = participant2Id,
                    participantStartNumber = 0,
                    participantFirstName = "",
                    participantLastName = "",
                ),
            )
        }

        return lanes
    }

    fun recordMatchResult(
        eventId: UUID,
        matchId: UUID,
        winnerId: UUID,
        heatId: UUID,
        actorId: UUID,
    ): RecordMatchResult {
        val tournament = knockoutRepository.findByEventId(eventId)
            ?: return RecordMatchResult.TournamentNotFound

        val matches = knockoutRepository.findMatchesByTournamentId(tournament.id)
        val match = matches.find { it.id == matchId }
            ?: return RecordMatchResult.MatchNotFound

        if (match.status != KnockoutMatchStatus.PLANNED) {
            return RecordMatchResult.MatchAlreadyCompleted
        }

        if (match.participant1Id != winnerId && match.participant2Id != winnerId) {
            return RecordMatchResult.WinnerNotInMatch
        }

        val now = clock.now()
        knockoutRepository.updateMatchResult(matchId, winnerId, heatId, KnockoutMatchStatus.COMPLETED)

        val nextRound = match.roundNumber + 1
        val nextRoundMatches = knockoutRepository.findMatchesByRound(tournament.id, nextRound)

        if (nextRoundMatches.isNotEmpty()) {
            val targetMatchNumber = ((match.matchNumber - 1) / 2) + 1
            val targetMatch = nextRoundMatches.find { it.matchNumber == targetMatchNumber }
            if (targetMatch != null) {
                val isFirstInPair = (match.matchNumber % 2) == 1
                if (isFirstInPair) {
                    knockoutRepository.updateMatchParticipants(targetMatch.id, winnerId, targetMatch.participant2Id)
                } else {
                    knockoutRepository.updateMatchParticipants(targetMatch.id, targetMatch.participant1Id, winnerId)
                }
            }
        }

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "KNOCKOUT_MATCH_RESULT",
                targetType = "KnockoutMatch",
                targetId = matchId,
                summary = "Match result recorded: winner=$winnerId",
                occurredAt = clock.now(),
            ),
        )

        return RecordMatchResult.Success
    }

    /**
     * Undo the recorded result of the completed match whose heat is [heatId] (used when that heat is
     * repeated). Clears the winner, resets the match to PLANNED, removes the winner from the next-round
     * slot it advanced into, and reopens the tournament if it was finalized. Blocked if that downstream
     * match has already been raced.
     */
    fun resetMatchForHeat(eventId: UUID, heatId: UUID, actorId: UUID): ResetMatchResult {
        val tournament = knockoutRepository.findByEventId(eventId)
            ?: return ResetMatchResult.NoMatch

        val matches = knockoutRepository.findMatchesByTournamentId(tournament.id)
        val match = matches.find { it.heatId == heatId }
            ?: return ResetMatchResult.NoMatch
        if (match.status != KnockoutMatchStatus.COMPLETED) {
            return ResetMatchResult.NoMatch
        }

        val nextRound = match.roundNumber + 1
        val targetMatchNumber = ((match.matchNumber - 1) / 2) + 1
        val target = matches.find { it.roundNumber == nextRound && it.matchNumber == targetMatchNumber }

        if (target != null && target.status == KnockoutMatchStatus.COMPLETED) {
            return ResetMatchResult.HasCompletedDependent
        }

        knockoutRepository.resetMatch(match.id)

        if (target != null) {
            val isFirstInPair = (match.matchNumber % 2) == 1
            if (isFirstInPair) {
                knockoutRepository.updateMatchParticipants(target.id, null, target.participant2Id)
            } else {
                knockoutRepository.updateMatchParticipants(target.id, target.participant1Id, null)
            }
        }

        if (tournament.status == KnockoutStatus.FINALIZED) {
            knockoutRepository.updateStatus(
                id = tournament.id,
                status = KnockoutStatus.IN_PROGRESS,
                updatedAt = clock.now(),
            )
        }

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "KNOCKOUT_MATCH_RESET",
                targetType = "KnockoutMatch",
                targetId = match.id,
                summary = "Match result reset for repeat",
                occurredAt = clock.now(),
            ),
        )

        return ResetMatchResult.Success
    }

    fun finalize(eventId: UUID, actorId: UUID): FinalizeKnockoutResult {
        val tournament = knockoutRepository.findByEventId(eventId)
            ?: return FinalizeKnockoutResult.TournamentNotFound

        if (tournament.status != KnockoutStatus.IN_PROGRESS) {
            return FinalizeKnockoutResult.InvalidStatus(tournament.status)
        }

        val matches = knockoutRepository.findMatchesByTournamentId(tournament.id)
        val incompleteMatches = matches.filter { it.status != KnockoutMatchStatus.COMPLETED }
        if (incompleteMatches.isNotEmpty()) {
            return FinalizeKnockoutResult.IncompleteMatches(incompleteMatches.size)
        }

        val now = clock.now()
        knockoutRepository.updateStatus(
            id = tournament.id,
            status = KnockoutStatus.FINALIZED,
            updatedAt = now,
            finalizedAt = now,
            finalizedBy = actorId,
        )

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "KNOCKOUT_FINALIZED",
                targetType = "KnockoutTournament",
                targetId = tournament.id,
                summary = "Knockout finalized",
                occurredAt = clock.now(),
            ),
        )

        return FinalizeKnockoutResult.Success
    }

    fun getResults(eventId: UUID): List<KnockoutResultEntry> {
        val tournament = knockoutRepository.findByEventId(eventId) ?: return emptyList()
        val matches = knockoutRepository.findMatchesByTournamentId(tournament.id)
        val rankings = getQualificationRankings(eventId)

        val finalRound = matches.maxOfOrNull { it.roundNumber } ?: return emptyList()
        val finalMatches = matches.filter { it.roundNumber == finalRound }
        val semifinalMatches = matches.filter { it.roundNumber == finalRound - 1 }

        val thirdPlaceMatch = semifinalMatches.find { it.matchNumber == semifinalMatches.size }
        val firstPlaceMatch = finalMatches.find { it.matchNumber == 1 }

        val results = mutableListOf<KnockoutResultEntry>()
        addFirstPlaceResult(results = results, firstPlaceMatch = firstPlaceMatch, rankings = rankings)
        addSecondPlaceResult(results = results, firstPlaceMatch = firstPlaceMatch, rankings = rankings)
        addThirdPlaceResult(results = results, thirdPlaceMatch = thirdPlaceMatch, rankings = rankings)

        return results
    }

    private fun getQualificationRankings(eventId: UUID): List<QualificationRanking> {
        val qualification = qualificationRepository.findByEventId(eventId) ?: return emptyList()
        val participants = participantRepository.findByEventId(eventId)
            .filter { it.status == ParticipantStatus.ACTIVE }
        val heats = heatRepository.findByEventId(eventId)
            .filter { it.round == 1 }

        return calculateRankings(participants, heats)
    }

    private fun calculateRankings(
        participants: List<io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity>,
        heats: List<HeatEntity>,
    ): List<QualificationRanking> {
        return QualificationRankingCalculator.calculate(
            participants = participants,
            heats = heats,
        )
    }

    private fun createPairings(
        tournamentId: UUID,
        participants: List<KnockoutRankedParticipant>,
        mode: PairingMode,
    ): List<KnockoutMatchEntity> {
        val sorted = participants.sortedBy { it.qualificationRank }
        val matchCount = sorted.size / 2
        val hasBye = sorted.size % 2 != 0

        val firstRoundMatches = when (mode) {
            PairingMode.FIRST_VS_LAST -> createFirstVsLastPairings(tournamentId, sorted, matchCount)
            PairingMode.ADJACENT -> createAdjacentPairings(tournamentId, sorted, matchCount)
            PairingMode.RANDOM -> createRandomPairings(tournamentId, sorted, matchCount)
            PairingMode.MANUAL -> createManualPairings(tournamentId, sorted, matchCount)
        }

        val matches = mutableListOf<KnockoutMatchEntity>()
        matches.addAll(firstRoundMatches)

        if (hasBye) {
            val byeParticipant = sorted.last()
            matches.add(
                KnockoutMatchEntity(
                    id = UUID.randomUUID(),
                    tournamentId = tournamentId,
                    roundNumber = 1,
                    matchNumber = matches.size + 1,
                    participant1Id = byeParticipant.participantId,
                    participant2Id = null,
                    status = KnockoutMatchStatus.COMPLETED,
                    winnerId = byeParticipant.participantId,
                    createdAt = clock.now(),
                ),
            )
        }

        matches.addAll(
            generateSubsequentRounds(
                tournamentId = tournamentId,
                firstRoundSize = matchCount + (if (hasBye) 1 else 0),
                createdAt = clock.now(),
            ),
        )

        return matches
    }

    private fun generateSubsequentRounds(
        tournamentId: UUID,
        firstRoundSize: Int,
        createdAt: kotlin.time.Instant,
    ): List<KnockoutMatchEntity> {
        val rounds = mutableListOf<KnockoutMatchEntity>()
        var currentRoundSize = firstRoundSize
        var roundNumber = 2
        while (currentRoundSize > 1) {
            val roundMatches = (1..currentRoundSize / 2).map { matchIndex ->
                KnockoutMatchEntity(
                    id = UUID.randomUUID(),
                    tournamentId = tournamentId,
                    roundNumber = roundNumber,
                    matchNumber = matchIndex,
                    status = KnockoutMatchStatus.PLANNED,
                    createdAt = createdAt,
                )
            }
            rounds.addAll(roundMatches)
            currentRoundSize = currentRoundSize / 2 + (if (currentRoundSize % 2 != 0) 1 else 0)
            roundNumber++
        }
        return rounds
    }

    private fun createFirstVsLastPairings(
        tournamentId: UUID,
        participants: List<KnockoutRankedParticipant>,
        matchCount: Int,
    ): List<KnockoutMatchEntity> {
        val shuffled = participants.toMutableList()
        val matches = mutableListOf<KnockoutMatchEntity>()

        for (i in 0 until matchCount) {
            val first = shuffled.removeFirst()
            val last = shuffled.removeLast()
            matches.add(
                KnockoutMatchEntity(
                    id = UUID.randomUUID(),
                    tournamentId = tournamentId,
                    roundNumber = 1,
                    matchNumber = i + 1,
                    participant1Id = first.participantId,
                    participant2Id = last.participantId,
                    status = KnockoutMatchStatus.PLANNED,
                    createdAt = clock.now(),
                ),
            )
        }

        return matches
    }

    private fun createAdjacentPairings(
        tournamentId: UUID,
        participants: List<KnockoutRankedParticipant>,
        matchCount: Int,
    ): List<KnockoutMatchEntity> {
        val matches = mutableListOf<KnockoutMatchEntity>()

        for (i in 0 until matchCount) {
            val first = participants[i * 2]
            val second = participants[i * 2 + 1]
            matches.add(
                KnockoutMatchEntity(
                    id = UUID.randomUUID(),
                    tournamentId = tournamentId,
                    roundNumber = 1,
                    matchNumber = i + 1,
                    participant1Id = first.participantId,
                    participant2Id = second.participantId,
                    status = KnockoutMatchStatus.PLANNED,
                    createdAt = clock.now(),
                ),
            )
        }

        return matches
    }

    private fun createRandomPairings(
        tournamentId: UUID,
        participants: List<KnockoutRankedParticipant>,
        matchCount: Int,
    ): List<KnockoutMatchEntity> {
        val shuffled = participants.shuffled(java.util.Random(clock.now().toEpochMilliseconds()))
        val matches = mutableListOf<KnockoutMatchEntity>()

        for (i in 0 until matchCount) {
            val first = shuffled[i * 2]
            val second = shuffled[i * 2 + 1]
            matches.add(
                KnockoutMatchEntity(
                    id = UUID.randomUUID(),
                    tournamentId = tournamentId,
                    roundNumber = 1,
                    matchNumber = i + 1,
                    participant1Id = first.participantId,
                    participant2Id = second.participantId,
                    status = KnockoutMatchStatus.PLANNED,
                    createdAt = clock.now(),
                ),
            )
        }

        return matches
    }

    private fun createManualPairings(
        tournamentId: UUID,
        participants: List<KnockoutRankedParticipant>,
        matchCount: Int,
    ): List<KnockoutMatchEntity> {
        val matches = mutableListOf<KnockoutMatchEntity>()

        for (i in 0 until matchCount) {
            val first = participants[i * 2]
            val second = if (i * 2 + 1 < participants.size) participants[i * 2 + 1] else null
            matches.add(
                KnockoutMatchEntity(
                    id = UUID.randomUUID(),
                    tournamentId = tournamentId,
                    roundNumber = 1,
                    matchNumber = i + 1,
                    participant1Id = first.participantId,
                    participant2Id = second?.participantId,
                    status = KnockoutMatchStatus.PLANNED,
                    createdAt = clock.now(),
                ),
            )
        }

        return matches
    }
}

private fun addFirstPlaceResult(
    results: MutableList<KnockoutResultEntry>,
    firstPlaceMatch: KnockoutMatchEntity?,
    rankings: List<QualificationRanking>,
) {
    val winnerId = firstPlaceMatch?.winnerId ?: return
    results.add(buildResultEntry(rank = 1, participantId = winnerId, rankings = rankings))
}

private fun addSecondPlaceResult(
    results: MutableList<KnockoutResultEntry>,
    firstPlaceMatch: KnockoutMatchEntity?,
    rankings: List<QualificationRanking>,
) {
    val loserOfFinal = firstPlaceMatch?.let { m ->
        if (m.winnerId == m.participant1Id) m.participant2Id else m.participant1Id
    } ?: return
    results.add(buildResultEntry(rank = 2, participantId = loserOfFinal, rankings = rankings))
}

private fun addThirdPlaceResult(
    results: MutableList<KnockoutResultEntry>,
    thirdPlaceMatch: KnockoutMatchEntity?,
    rankings: List<QualificationRanking>,
) {
    val winnerId = thirdPlaceMatch?.winnerId ?: return
    results.add(buildResultEntry(rank = 3, participantId = winnerId, rankings = rankings))
}

private fun buildResultEntry(
    rank: Int,
    participantId: UUID,
    rankings: List<QualificationRanking>,
): KnockoutResultEntry {
    val ranking = rankings.find { it.participantId == participantId }
    return KnockoutResultEntry(
        rank = rank,
        participantId = participantId,
        firstName = ranking?.firstName ?: "",
        lastName = ranking?.lastName ?: "",
        startNumber = ranking?.startNumber ?: 0,
        club = ranking?.club,
    )
}
