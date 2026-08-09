package io.github.raginlundf.racingmanager.application.knockout

import io.github.raginlundf.racingmanager.application.qualification.QualificationRankingCalculator
import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
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

@Suppress("TooManyFunctions")
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

        advanceByes(matches)

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

        advanceByes(matches)

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

        // Idempotent: a match whose heat already exists just returns that heat, so a double-click
        // or re-navigation is harmless instead of a misleading 409.
        val existingHeatId = match.heatId
        if (match.status == KnockoutMatchStatus.IN_PROGRESS && existingHeatId != null) {
            heatRepository.findById(existingHeatId)?.let { return CreateHeatForMatchResult.Success(it) }
        }

        if (match.status != KnockoutMatchStatus.PLANNED) {
            return CreateHeatForMatchResult.MatchAlreadyCompleted
        }

        val participant1Id = match.participant1Id
        val participant2Id = match.participant2Id

        // A PLANNED match with an empty slot is still waiting on a feeder winner (true byes are
        // COMPLETED at pairing generation and never reach here), so it is not ready to race.
        if (participant1Id == null || participant2Id == null) {
            return CreateHeatForMatchResult.MissingParticipants
        }

        val existingHeats = heatRepository.findByEventId(eventId)
        // Per-phase counter: knockout heats number from #1 independently of qualification.
        val heatNumber = existingHeats.count { it.round == 2 } + 1

        val now = clock.now()
        val lanes = buildKnockoutHeatLanes(
            eventId = eventId,
            participant1Id = participant1Id,
            participant2Id = participant2Id,
        )

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
        // Link the heat to the match and move it forward so the UI shows a "Run" action
        // and the accept flow can auto-record the winner from this heat's timing.
        knockoutRepository.updateMatchHeat(
            id = matchId,
            heatId = heat.id,
            status = KnockoutMatchStatus.IN_PROGRESS,
        )

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

    private fun buildKnockoutHeatLanes(
        eventId: UUID,
        participant1Id: UUID,
        participant2Id: UUID?,
    ): List<HeatLaneAssignment> {
        val byId = participantRepository.findByEventId(eventId).associateBy { it.id }
        fun lane(number: Int, participantId: UUID): HeatLaneAssignment {
            val p = byId[participantId]
            return HeatLaneAssignment(
                lane = number,
                participantId = participantId,
                participantStartNumber = p?.startNumber ?: 0,
                participantFirstName = p?.firstName ?: "",
                participantLastName = p?.lastName ?: "",
            )
        }

        val lanes = mutableListOf(lane(number = 1, participantId = participant1Id))
        if (participant2Id != null) {
            lanes.add(lane(number = 2, participantId = participant2Id))
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

        if (match.status == KnockoutMatchStatus.COMPLETED) {
            return RecordMatchResult.MatchAlreadyCompleted
        }

        if (match.participant1Id != winnerId && match.participant2Id != winnerId) {
            return RecordMatchResult.WinnerNotInMatch
        }

        knockoutRepository.updateMatchResult(matchId, winnerId, heatId, KnockoutMatchStatus.COMPLETED)
        advanceWinner(sourceMatch = match, winnerId = winnerId)

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
     * Propagate [winnerId] from a completed [sourceMatch] into its next-round slot. If the target's
     * sibling feeder match does not exist (odd feeder round), the target can only ever hold this one
     * participant, so it is completed as a bye and advanced recursively.
     */
    private fun advanceWinner(sourceMatch: KnockoutMatchEntity, winnerId: UUID) {
        val nextRoundMatches = knockoutRepository.findMatchesByRound(
            tournamentId = sourceMatch.tournamentId,
            roundNumber = sourceMatch.roundNumber + 1
        )
        if (nextRoundMatches.isEmpty()) return
        val targetNumber = ((sourceMatch.matchNumber - 1) / 2) + 1
        val target = nextRoundMatches.find { it.matchNumber == targetNumber } ?: return

        val isFirstInPair = (sourceMatch.matchNumber % 2) == 1
        if (isFirstInPair) {
            knockoutRepository.updateMatchParticipants(target.id, winnerId, target.participant2Id)
        } else {
            knockoutRepository.updateMatchParticipants(target.id, target.participant1Id, winnerId)
        }

        val siblingNumber = if (isFirstInPair) sourceMatch.matchNumber + 1 else sourceMatch.matchNumber - 1
        val feederRound = knockoutRepository.findMatchesByRound(sourceMatch.tournamentId, sourceMatch.roundNumber)
        if (feederRound.none { it.matchNumber == siblingNumber }) {
            knockoutRepository.completeBye(target.id, winnerId)
            advanceWinner(sourceMatch = target, winnerId = winnerId)
        }
    }

    /**
     * Derive and record the winner of the knockout match tied to [heatId] from that heat's timing
     * (fastest FINISHED lane), advancing the bracket. Called best-effort when a heat result is accepted
     * — the knockout equivalent of qualification reading results from the hardware/simulation.
     */
    fun recordResultFromHeat(eventId: UUID, heatId: UUID, actorId: UUID): RecordResultFromHeatResult {
        val tournament = knockoutRepository.findByEventId(eventId)
            ?: return RecordResultFromHeatResult.NoMatch
        val match = knockoutRepository.findMatchesByTournamentId(tournament.id).find { it.heatId == heatId }
            ?: return RecordResultFromHeatResult.NoMatch
        if (match.status == KnockoutMatchStatus.COMPLETED) {
            return RecordResultFromHeatResult.NoMatch
        }

        val heat = heatRepository.findById(heatId)
            ?: return RecordResultFromHeatResult.NoMatch

        val winnerId = heat.lanes
            .mapNotNull { lane ->
                heat.measurements
                    .firstOrNull { it.lane == lane.lane && it.outcome == LaneOutcome.FINISHED }
                    ?.let { lane.participantId to it.durationNanos }
            }
            .minByOrNull { it.second }
            ?.first
            ?: return RecordResultFromHeatResult.NoWinner

        return when (recordMatchResult(eventId, match.id, winnerId, heatId, actorId)) {
            is RecordMatchResult.Success -> RecordResultFromHeatResult.Success
            else -> RecordResultFromHeatResult.NoWinner
        }
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
        val participants = participantRepository.findByEventId(eventId = eventId)
            .filter { it.status == ParticipantStatus.ACTIVE }
        val heats = heatRepository.findByEventId(eventId = eventId)
            .filter { it.round == 1 }

        return calculateRankings(participants = participants, heats = heats)
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
        // Pad to a full single-elimination bracket; the strongest seeds get the byes.
        val bracketSize = nextPowerOfTwo(sorted.size)
        val byeCount = bracketSize - sorted.size
        val byeSeeds = sorted.take(byeCount)
        val racing = sorted.drop(byeCount)

        val racingPairs = when (mode) {
            PairingMode.FIRST_VS_LAST -> firstVsLastPairs(racing)
            PairingMode.RANDOM -> adjacentPairs(racing.shuffled(java.util.Random(clock.now().toEpochMilliseconds())))
            else -> adjacentPairs(racing) // ADJACENT / MANUAL fall back to seed-order pairing here
        }

        val now = clock.now()
        val matches = mutableListOf<KnockoutMatchEntity>()
        var matchNumber = 1

        // Byes first (matchNumbers 1..byeCount): auto-completed, advanced after insertion.
        for (bye in byeSeeds) {
            matches.add(
                KnockoutMatchEntity(
                    id = UUID.randomUUID(),
                    tournamentId = tournamentId,
                    roundNumber = 1,
                    matchNumber = matchNumber++,
                    participant1Id = bye.participantId,
                    participant2Id = null,
                    status = KnockoutMatchStatus.COMPLETED,
                    winnerId = bye.participantId,
                    createdAt = now,
                ),
            )
        }
        for ((p1, p2) in racingPairs) {
            matches.add(
                KnockoutMatchEntity(
                    id = UUID.randomUUID(),
                    tournamentId = tournamentId,
                    roundNumber = 1,
                    matchNumber = matchNumber++,
                    participant1Id = p1.participantId,
                    participant2Id = p2.participantId,
                    status = KnockoutMatchStatus.PLANNED,
                    createdAt = now,
                ),
            )
        }

        matches.addAll(
            generateSubsequentRounds(
                tournamentId = tournamentId,
                firstRoundSize = bracketSize / 2,
                createdAt = now,
            ),
        )
        return matches
    }

    private fun firstVsLastPairs(
        participants: List<KnockoutRankedParticipant>,
    ): List<Pair<KnockoutRankedParticipant, KnockoutRankedParticipant>> {
        val remaining = participants.toMutableList()
        val pairs = mutableListOf<Pair<KnockoutRankedParticipant, KnockoutRankedParticipant>>()
        while (remaining.size >= 2) {
            pairs.add(remaining.removeFirst() to remaining.removeLast())
        }
        return pairs
    }

    private fun adjacentPairs(
        participants: List<KnockoutRankedParticipant>,
    ): List<Pair<KnockoutRankedParticipant, KnockoutRankedParticipant>> {
        return participants.chunked(2).filter { it.size == 2 }.map { it[0] to it[1] }
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var size = 1
        while (size < n) size *= 2
        return size
    }

    /** Propagate every round-1 bye winner into the next round (recursively resolving chained byes). */
    private fun advanceByes(matches: List<KnockoutMatchEntity>) {
        matches
            .filter { it.roundNumber == 1 && it.participant2Id == null && it.winnerId != null }
            .forEach { advanceWinner(sourceMatch = it, winnerId = it.winnerId!!) }
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
            val nextSize = (currentRoundSize + 1) / 2 // ceil: every winner needs a target match
            rounds.addAll(
                (1..nextSize).map { matchIndex ->
                    KnockoutMatchEntity(
                        id = UUID.randomUUID(),
                        tournamentId = tournamentId,
                        roundNumber = roundNumber,
                        matchNumber = matchIndex,
                        status = KnockoutMatchStatus.PLANNED,
                        createdAt = createdAt,
                    )
                },
            )
            currentRoundSize = nextSize
            roundNumber++
        }
        return rounds
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
