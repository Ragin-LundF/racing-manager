package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchEntity
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutStatus
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutTournamentEntity
import io.github.raginlundf.racingmanager.domain.knockout.PairingMode
import io.github.raginlundf.racingmanager.infrastructure.tables.KnockoutMatchTable
import io.github.raginlundf.racingmanager.infrastructure.tables.KnockoutTournamentTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class KnockoutRepository {

    fun findByEventId(eventId: UUID): KnockoutTournamentEntity? {
        return transaction {
            KnockoutTournamentTable.selectAll()
                .where { KnockoutTournamentTable.eventId eq eventId }
                .singleOrNull()
                ?.let { row -> mapTournamentRow(row = row) }
        }
    }

    fun findById(id: UUID): KnockoutTournamentEntity? {
        return transaction {
            KnockoutTournamentTable.selectAll()
                .where { KnockoutTournamentTable.id eq id }
                .singleOrNull()
                ?.let { row -> mapTournamentRow(row = row) }
        }
    }

    fun insert(tournament: KnockoutTournamentEntity) {
        transaction {
            KnockoutTournamentTable.insert {
                it[id] = tournament.id
                it[eventId] = tournament.eventId
                it[status] = tournament.status.name
                it[pairingMode] = tournament.pairingMode.name
                it[qualificationId] = tournament.qualificationId
                it[createdAt] = tournament.createdAt
                it[updatedAt] = tournament.updatedAt
                it[finalizedAt] = tournament.finalizedAt
                it[finalizedBy] = tournament.finalizedBy
            }
        }
    }

    fun updateStatus(id: UUID, status: KnockoutStatus, updatedAt: kotlin.time.Instant, finalizedAt: kotlin.time.Instant? = null, finalizedBy: UUID? = null) {
        transaction {
            KnockoutTournamentTable.update(where = { KnockoutTournamentTable.id eq id }) {
                it[KnockoutTournamentTable.status] = status.name
                it[KnockoutTournamentTable.updatedAt] = updatedAt
                if (finalizedAt != null) it[KnockoutTournamentTable.finalizedAt] = finalizedAt
                if (finalizedBy != null) it[KnockoutTournamentTable.finalizedBy] = finalizedBy
            }
        }
    }

    fun findMatchesByTournamentId(tournamentId: UUID): List<KnockoutMatchEntity> {
        return transaction {
            KnockoutMatchTable.selectAll()
                .where { KnockoutMatchTable.tournamentId eq tournamentId }
                .orderBy(KnockoutMatchTable.roundNumber to SortOrder.ASC, KnockoutMatchTable.matchNumber to SortOrder.ASC)
                .map { row -> mapMatchRow(row = row) }
        }
    }

    fun findMatchesByRound(tournamentId: UUID, roundNumber: Int): List<KnockoutMatchEntity> {
        return transaction {
            KnockoutMatchTable.selectAll()
                .where { KnockoutMatchTable.tournamentId eq tournamentId and (KnockoutMatchTable.roundNumber eq roundNumber) }
                .orderBy(KnockoutMatchTable.matchNumber to SortOrder.ASC)
                .map { row -> mapMatchRow(row = row) }
        }
    }

    fun insertMatch(match: KnockoutMatchEntity) {
        transaction {
            KnockoutMatchTable.insert {
                it[id] = match.id
                it[tournamentId] = match.tournamentId
                it[roundNumber] = match.roundNumber
                it[matchNumber] = match.matchNumber
                it[participant1Id] = match.participant1Id
                it[participant2Id] = match.participant2Id
                it[winnerId] = match.winnerId
                it[heatId] = match.heatId
                it[status] = match.status.name
                it[createdAt] = match.createdAt
            }
        }
    }

    fun updateMatchParticipants(id: UUID, participant1Id: UUID?, participant2Id: UUID?) {
        transaction {
            KnockoutMatchTable.update(where = { KnockoutMatchTable.id eq id }) {
                it[KnockoutMatchTable.participant1Id] = participant1Id
                it[KnockoutMatchTable.participant2Id] = participant2Id
            }
        }
    }

    fun updateMatchResult(id: UUID, winnerId: UUID, heatId: UUID, status: KnockoutMatchStatus) {
        transaction {
            KnockoutMatchTable.update(where = { KnockoutMatchTable.id eq id }) {
                it[KnockoutMatchTable.winnerId] = winnerId
                it[KnockoutMatchTable.heatId] = heatId
                it[KnockoutMatchTable.status] = status.name
            }
        }
    }

    fun deleteAll() {
        transaction {
            KnockoutMatchTable.deleteAll()
            KnockoutTournamentTable.deleteAll()
        }
    }

    private fun mapTournamentRow(row: ResultRow): KnockoutTournamentEntity {
        return KnockoutTournamentEntity(
            id = row[KnockoutTournamentTable.id],
            eventId = row[KnockoutTournamentTable.eventId],
            status = KnockoutStatus.valueOf(row[KnockoutTournamentTable.status]),
            pairingMode = PairingMode.valueOf(row[KnockoutTournamentTable.pairingMode]),
            qualificationId = row[KnockoutTournamentTable.qualificationId],
            createdAt = row[KnockoutTournamentTable.createdAt],
            updatedAt = row[KnockoutTournamentTable.updatedAt],
            finalizedAt = row[KnockoutTournamentTable.finalizedAt],
            finalizedBy = row[KnockoutTournamentTable.finalizedBy],
        )
    }

    private fun mapMatchRow(row: ResultRow): KnockoutMatchEntity {
        return KnockoutMatchEntity(
            id = row[KnockoutMatchTable.id],
            tournamentId = row[KnockoutMatchTable.tournamentId],
            roundNumber = row[KnockoutMatchTable.roundNumber],
            matchNumber = row[KnockoutMatchTable.matchNumber],
            participant1Id = row[KnockoutMatchTable.participant1Id],
            participant2Id = row[KnockoutMatchTable.participant2Id],
            winnerId = row[KnockoutMatchTable.winnerId],
            heatId = row[KnockoutMatchTable.heatId],
            status = KnockoutMatchStatus.valueOf(row[KnockoutMatchTable.status]),
            createdAt = row[KnockoutMatchTable.createdAt],
        )
    }
}
