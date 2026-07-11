package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.domain.heat.Measurement
import io.github.raginlundf.racingmanager.infrastructure.tables.HeatLaneTable
import io.github.raginlundf.racingmanager.infrastructure.tables.HeatTable
import io.github.raginlundf.racingmanager.infrastructure.tables.MeasurementTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import java.util.UUID

class HeatRepository {

    fun findById(id: UUID): HeatEntity? = transaction {
        HeatTable.selectAll().where { HeatTable.id eq id }
            .singleOrNull()
            ?.let { row -> mapRow(row) }
    }

    fun findByEventId(eventId: UUID): List<HeatEntity> = transaction {
        HeatTable.selectAll()
            .where { HeatTable.eventId eq eventId }
            .orderBy(HeatTable.round to SortOrder.ASC, HeatTable.heatNumber to SortOrder.ASC)
            .map { row -> mapRow(row) }
    }

    fun findEventIdByHeatId(heatId: UUID): UUID? = transaction {
        HeatTable.selectAll().where { HeatTable.id eq heatId }
            .singleOrNull()
            ?.let { row -> row[HeatTable.eventId] }
    }

    fun findLatestByEventId(eventId: UUID): HeatEntity? = transaction {
        HeatTable.selectAll()
            .where { HeatTable.eventId eq eventId }
            .orderBy(HeatTable.createdAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let { row -> mapRow(row) }
    }

    fun insert(heat: HeatEntity) = transaction {
        HeatTable.insert {
            it[id] = heat.id
            it[eventId] = heat.eventId
            it[round] = heat.round
            it[heatNumber] = heat.heatNumber
            it[status] = heat.status.name
            it[createdAt] = heat.createdAt
            it[armedAt] = heat.armedAt
            it[startedAt] = heat.startedAt
            it[finishedAt] = heat.finishedAt
        }
        heat.lanes.forEach { assignment ->
            HeatLaneTable.insert {
                it[id] = UUID.randomUUID()
                it[heatId] = heat.id
                it[HeatLaneTable.lane] = assignment.lane
                it[participantId] = assignment.participantId
                it[participantStartNumber] = assignment.participantStartNumber
                it[participantFirstName] = assignment.participantFirstName
                it[participantLastName] = assignment.participantLastName
            }
        }
    }

    fun updateStatus(id: UUID, status: HeatStatus, armedAt: Instant? = null, startedAt: Instant? = null, finishedAt: Instant? = null) = transaction {
        HeatTable.update({ HeatTable.id eq id }) {
            it[HeatTable.status] = status.name
            if (armedAt != null) it[HeatTable.armedAt] = armedAt
            if (startedAt != null) it[HeatTable.startedAt] = startedAt
            if (finishedAt != null) it[HeatTable.finishedAt] = finishedAt
        }
    }

    fun insertMeasurement(measurement: Measurement) = transaction {
        MeasurementTable.insert {
            it[id] = measurement.id
            it[heatId] = measurement.heatId
            it[lane] = measurement.lane
            it[durationNanos] = measurement.durationNanos
            it[outcome] = measurement.outcome.name
            it[receivedAt] = measurement.receivedAt
        }
    }

    fun deleteAll() = transaction {
        MeasurementTable.deleteAll()
        HeatLaneTable.deleteAll()
        HeatTable.deleteAll()
    }

    private fun mapRow(row: ResultRow): HeatEntity {
        val heatId = row[HeatTable.id]
        val lanes = HeatLaneTable.selectAll()
            .where { HeatLaneTable.heatId eq heatId }
            .orderBy(HeatLaneTable.lane to SortOrder.ASC)
            .map { laneRow ->
                HeatLaneAssignment(
                    lane = laneRow[HeatLaneTable.lane],
                    participantId = laneRow[HeatLaneTable.participantId],
                    participantStartNumber = laneRow[HeatLaneTable.participantStartNumber],
                    participantFirstName = laneRow[HeatLaneTable.participantFirstName],
                    participantLastName = laneRow[HeatLaneTable.participantLastName],
                )
            }
        val measurements = MeasurementTable.selectAll()
            .where { MeasurementTable.heatId eq heatId }
            .orderBy(MeasurementTable.lane to SortOrder.ASC)
            .map { mRow ->
                Measurement(
                    id = mRow[MeasurementTable.id],
                    heatId = mRow[MeasurementTable.heatId],
                    lane = mRow[MeasurementTable.lane],
                    durationNanos = mRow[MeasurementTable.durationNanos],
                    outcome = LaneOutcome.valueOf(mRow[MeasurementTable.outcome]),
                    receivedAt = mRow[MeasurementTable.receivedAt],
                )
            }
        return HeatEntity(
            id = heatId,
            eventId = row[HeatTable.eventId],
            round = row[HeatTable.round],
            heatNumber = row[HeatTable.heatNumber],
            status = HeatStatus.valueOf(row[HeatTable.status]),
            lanes = lanes,
            measurements = measurements,
            createdAt = row[HeatTable.createdAt],
            armedAt = row[HeatTable.armedAt],
            startedAt = row[HeatTable.startedAt],
            finishedAt = row[HeatTable.finishedAt],
        )
    }
}
