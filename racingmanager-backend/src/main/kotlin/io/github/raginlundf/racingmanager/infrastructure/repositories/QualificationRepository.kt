package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.qualification.QualificationEntity
import io.github.raginlundf.racingmanager.domain.qualification.QualificationStatus
import io.github.raginlundf.racingmanager.infrastructure.tables.QualificationTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class QualificationRepository {

    fun findByEventId(eventId: UUID): QualificationEntity? {
        return transaction {
            QualificationTable.selectAll()
                .where { QualificationTable.eventId eq eventId }
                .singleOrNull()
                ?.let { row -> mapRow(row) }
        }
    }

    fun findById(id: UUID): QualificationEntity? {
        return transaction {
            QualificationTable.selectAll()
                .where { QualificationTable.id eq id }
                .singleOrNull()
                ?.let { row -> mapRow(row) }
        }
    }

    fun insert(qualification: QualificationEntity) {
        transaction {
            QualificationTable.insert {
                it[id] = qualification.id
                it[eventId] = qualification.eventId
                it[status] = qualification.status.name
                it[numberOfRuns] = qualification.numberOfRuns
                it[seed] = qualification.seed
                it[createdAt] = qualification.createdAt
                it[updatedAt] = qualification.updatedAt
                it[finalizedAt] = qualification.finalizedAt
                it[finalizedBy] = qualification.finalizedBy
            }
        }
    }

    fun updateStatus(id: UUID, status: QualificationStatus, updatedAt: kotlin.time.Instant, finalizedAt: kotlin.time.Instant? = null, finalizedBy: UUID? = null) {
        transaction {
            QualificationTable.update({ QualificationTable.id eq id }) {
                it[QualificationTable.status] = status.name
                it[QualificationTable.updatedAt] = updatedAt
                if (finalizedAt != null) it[QualificationTable.finalizedAt] = finalizedAt
                if (finalizedBy != null) it[QualificationTable.finalizedBy] = finalizedBy
            }
        }
    }

    fun deleteAll() {
        transaction {
            QualificationTable.deleteAll()
        }
    }

    private fun mapRow(row: ResultRow): QualificationEntity {
        return QualificationEntity(
            id = row[QualificationTable.id],
            eventId = row[QualificationTable.eventId],
            status = QualificationStatus.valueOf(row[QualificationTable.status]),
            numberOfRuns = row[QualificationTable.numberOfRuns],
            seed = row[QualificationTable.seed],
            createdAt = row[QualificationTable.createdAt],
            updatedAt = row[QualificationTable.updatedAt],
            finalizedAt = row[QualificationTable.finalizedAt],
            finalizedBy = row[QualificationTable.finalizedBy],
        )
    }
}
