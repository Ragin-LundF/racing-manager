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
                HeatLaneAssignment(1, participant.id, participant.startNumber, participant.firstName, participant.lastName),
            ),
            measurements = if (outcome == null) emptyList() else listOf(
                Measurement(UUID.randomUUID(), heatId, 1, nanos, outcome, epoch),
            ),
            createdAt = epoch,
        )
    }

    @Test
    fun `participants with a time rank above DNF-only and no-run participants`() {
        val alice = participant("Alice", 1)
        val bob = participant("Bob", 2)
        val dave = participant("Dave", 3)
        val erin = participant("Erin", 4)
        val heats = listOf(
            heat(alice, LaneOutcome.FINISHED, 1_000_000_000),
            heat(bob, LaneOutcome.FINISHED, 2_000_000_000),
            heat(dave, LaneOutcome.DNF, 0),
            heat(erin, null, 0),
        )

        val ranked = QualificationRankingCalculator.calculate(listOf(alice, bob, dave, erin), heats)

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
        val alice = participant("Alice", 1)
        val bob = participant("Bob", 2)
        val heats = listOf(
            heat(alice, LaneOutcome.FINISHED, 1_000_000_000),
            heat(bob, LaneOutcome.FINISHED, 1_000_000_000),
        )

        val ranked = QualificationRankingCalculator.calculate(listOf(alice, bob), heats)

        assertEquals(expected = 1, actual = ranked[0].rank)
        assertEquals(expected = 1, actual = ranked[1].rank)
    }
}
