package io.github.raginlundf.racingmanager.application.qualification

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.domain.heat.Measurement
import io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class QualificationRankingCalculatorTest {

    private val eventId = UUID.randomUUID()
    private val epoch = Instant.fromEpochMilliseconds(0)

    private fun participant(name: String, startNumber: Int): ParticipantEntity =
        ParticipantEntity(
            id = UUID.randomUUID(),
            eventId = eventId,
            startNumber = startNumber,
            firstName = name,
            lastName = "X",
            createdAt = epoch,
        )

    /** A single-lane heat for [participant] with an optional measurement of [outcome]/[nanos]. */
    private fun heat(participant: ParticipantEntity, outcome: LaneOutcome?, nanos: Long): HeatEntity {
        val heatId = UUID.randomUUID()
        return HeatEntity(
            id = heatId,
            eventId = eventId,
            round = 1,
            heatNumber = 1,
            status = HeatStatus.FINISHED,
            lanes = listOf(
                HeatLaneAssignment(
                    lane = 1,
                    participantId = participant.id,
                    participantStartNumber = participant.startNumber,
                    participantFirstName = participant.firstName,
                    participantLastName = participant.lastName
                ),
            ),
            measurements = if (outcome == null) emptyList() else listOf(
                Measurement(
                    id = UUID.randomUUID(),
                    heatId = heatId,
                    lane = 1,
                    durationNanos = nanos,
                    outcome = outcome,
                    receivedAt = epoch
                ),
            ),
            createdAt = epoch,
        )
    }

    @Test
    fun `participants with a time rank above DNF-only and no-run participants`() {
        val alice = participant(name = "Alice", startNumber = 1)
        val bob = participant(name = "Bob", startNumber = 2)
        val dave = participant(name = "Dave", startNumber = 3)
        val erin = participant(name = "Erin", startNumber = 4)
        val heats = listOf(
            heat(participant = alice, outcome = LaneOutcome.FINISHED, nanos = 1_000_000_000),
            heat(participant = bob, outcome = LaneOutcome.FINISHED, nanos = 2_000_000_000),
            heat(participant = dave, outcome = LaneOutcome.DNF, nanos = 0),
            heat(participant = erin, outcome = null, nanos = 0),
        )

        val ranked = QualificationRankingCalculator.calculate(
            participants = listOf(alice, bob, dave, erin),
            heats = heats
        )

        assertEquals(expected = alice.id, actual = ranked[0].participantId)
        assertEquals(expected = 1, actual = ranked[0].rank)
        assertEquals(expected = bob.id, actual = ranked[1].participantId)
        assertEquals(expected = 2, actual = ranked[1].rank)
        // The two no-time participants sink to the bottom as last places.
        val tail = ranked.drop(2)
        assertEquals(expected = setOf(dave.id, erin.id), actual = tail.map { it.participantId }.toSet())
        tail.forEach {
            assertNull(actual = it.bestTimeNanos)
            assertEquals(expected = 0, actual = it.completedRuns)
        }
        assertEquals(expected = setOf(3, 4), actual = tail.map { it.rank }.toSet())
    }

    @Test
    fun `equal best times share a rank`() {
        val alice = participant(name = "Alice", startNumber = 1)
        val bob = participant(name = "Bob", startNumber = 2)
        val heats = listOf(
            heat(participant = alice, outcome = LaneOutcome.FINISHED, nanos = 1_000_000_000),
            heat(participant = bob, outcome = LaneOutcome.FINISHED, nanos = 1_000_000_000),
        )

        val ranked = QualificationRankingCalculator.calculate(participants = listOf(alice, bob), heats = heats)

        assertEquals(expected = 1, actual = ranked[0].rank)
        assertEquals(expected = 1, actual = ranked[1].rank)
    }
}
