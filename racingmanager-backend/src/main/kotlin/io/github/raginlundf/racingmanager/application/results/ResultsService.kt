package io.github.raginlundf.racingmanager.application.results

import io.github.raginlundf.racingmanager.api.results.models.BackupResponseModel
import io.github.raginlundf.racingmanager.application.qualification.QualificationRankingCalculator
import io.github.raginlundf.racingmanager.domain.event.MeasurementType
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchEntity
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutMatchStatus
import io.github.raginlundf.racingmanager.application.knockout.KnockoutResultEntry
import io.github.raginlundf.racingmanager.domain.knockout.KnockoutTournamentEntity
import io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity
import io.github.raginlundf.racingmanager.domain.participant.ParticipantStatus
import io.github.raginlundf.racingmanager.domain.qualification.QualificationEntity
import io.github.raginlundf.racingmanager.domain.qualification.QualificationRanking
import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.KnockoutRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.QualificationRepository
import kotlin.time.Clock
import java.util.UUID

data class EventResultSnapshot(
    val event: EventEntity,
    val qualificationRankings: List<QualificationRanking>,
    val knockoutResults: List<KnockoutResultEntry>,
    val allHeats: List<HeatEntity>,
    val isSimulated: Boolean,
)

data class CsvExport(
    val csv: String,
    val filename: String,
)

data class HtmlExport(
    val html: String,
    val filename: String,
)

data class JsonExport(
    val schemaVersion: Int,
    val exportedAt: String,
    val snapshot: EventResultSnapshot,
)

data class BackupExport(
    val schemaVersion: Int,
    val exportedAt: String,
    val snapshot: EventResultSnapshot,
)

sealed interface RestoreResult {
    data class Success(val event: EventEntity) : RestoreResult
    data object NotFound : RestoreResult
    data class InvalidStatus(val status: EventStatus) : RestoreResult
    data object SnapshotMismatch : RestoreResult
}

class ResultsService(
    private val eventRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val heatRepository: HeatRepository,
    private val qualificationRepository: QualificationRepository,
    private val knockoutRepository: KnockoutRepository,
    private val auditRepository: AuditRepository,
) {
    private val clock: Clock = Clock.System

    fun getSnapshot(eventId: UUID): EventResultSnapshot? {
        val event = eventRepository.findById(eventId) ?: return null
        val participants = participantRepository.findByEventId(eventId)
            .filter { it.status == ParticipantStatus.ACTIVE }
        val heats = heatRepository.findByEventId(eventId)
        val qualification = qualificationRepository.findByEventId(eventId)
        val tournament = knockoutRepository.findByEventId(eventId)
        val matches = if (tournament != null) knockoutRepository.findMatchesByTournamentId(tournament.id) else emptyList()

        val qualificationRankings = if (qualification != null) {
            calculateRankings(participants, heats.filter { it.round == 1 }, qualification)
        } else emptyList()

        val knockoutResults = if (tournament != null) {
            calculateKnockoutResults(tournament, matches, qualificationRankings)
        } else emptyList()

        return EventResultSnapshot(
            event = event,
            qualificationRankings = qualificationRankings,
            knockoutResults = knockoutResults,
            allHeats = heats,
            isSimulated = event.settings.measurementType == MeasurementType.SIMULATED,
        )
    }

    fun exportCsv(eventId: UUID): CsvExport? {
        val snapshot = getSnapshot(eventId) ?: return null
        val sb = StringBuilder()
        sb.appendLine("Rank,StartNumber,FirstName,LastName,Club,BestTimeNanos,TotalTimeNanos,CompletedRuns,DNFCount")
        for (entry in snapshot.qualificationRankings) {
            sb.appendLine("${entry.rank},${entry.startNumber},${entry.firstName},${entry.lastName},${entry.club ?: ""},${entry.bestTimeNanos ?: ""},${entry.totalTimeNanos ?: ""},${entry.completedRuns},${entry.dnfCount}")
        }
        return CsvExport(
            csv = sb.toString(),
            filename = "results-${snapshot.event.name.replace(" ", "_")}.csv",
        )
    }

    fun exportHtml(eventId: UUID, locale: String): HtmlExport? {
        val snapshot = getSnapshot(eventId) ?: return null
        val rows = snapshot.qualificationRankings.joinToString("") { entry ->
            "<tr><td>${entry.rank}</td><td>${entry.startNumber}</td><td>${entry.firstName}</td><td>${entry.lastName}</td><td>${entry.club ?: ""}</td><td>${entry.bestTimeNanos ?: ""}</td><td>${entry.totalTimeNanos ?: ""}</td></tr>"
        }
        val html = """
            <!DOCTYPE html>
            <html lang="${locale.take(2)}">
            <head><meta charset="UTF-8"><title>${snapshot.event.name} - Results</title></head>
            <body>
            <h1>${snapshot.event.name}</h1>
            <table border="1"><tr><th>Rank</th><th>Start#</th><th>First</th><th>Last</th><th>Club</th><th>Best</th><th>Total</th></tr>$rows</table>
            </body>
            </html>
        """.trimIndent()
        return HtmlExport(
            html = html,
            filename = "results-${snapshot.event.name.replace(" ", "_")}.html",
        )
    }

    fun exportJson(eventId: UUID): JsonExport? {
        val snapshot = getSnapshot(eventId) ?: return null
        return JsonExport(
            schemaVersion = 1,
            exportedAt = clock.now().toString(),
            snapshot = snapshot,
        )
    }

    fun exportBackup(eventId: UUID): BackupExport? {
        val snapshot = getSnapshot(eventId) ?: return null
        return BackupExport(
            schemaVersion = 1,
            exportedAt = clock.now().toString(),
            snapshot = snapshot,
        )
    }

    fun restoreFromBackup(eventId: UUID, backup: BackupResponseModel, actorId: UUID): RestoreResult {
        val event = eventRepository.findById(eventId)
            ?: return RestoreResult.NotFound

        if (event.status != EventStatus.ACTIVE) {
            return RestoreResult.InvalidStatus(event.status)
        }

        if (backup.event.event.id != eventId.toString()) {
            return RestoreResult.SnapshotMismatch
        }

        val now = clock.now()
        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "EVENT_RESTORED",
                targetType = "Event",
                targetId = eventId,
                summary = "Event '''" + event.name + "'' restored from backup",
                occurredAt = now,
            ),
        )
        return RestoreResult.Success(event)
    }

    private fun calculateRankings(
        participants: List<ParticipantEntity>,
        heats: List<HeatEntity>,
        qualification: QualificationEntity,
    ): List<QualificationRanking> =
        QualificationRankingCalculator.calculate(
            participants = participants,
            heats = heats,
            qualification = qualification,
        )

    private fun calculateKnockoutResults(
        tournament: KnockoutTournamentEntity,
        matches: List<KnockoutMatchEntity>,
        qualificationRankings: List<QualificationRanking>,
    ): List<KnockoutResultEntry> {
        val winners = matches.filter { it.winnerId != null && it.status == KnockoutMatchStatus.COMPLETED }
            .sortedByDescending { it.roundNumber }
        val ranked = mutableListOf<KnockoutResultEntry>()
        var rank = 1
        for (match in winners) {
            val participant = qualificationRankings.find { it.participantId == match.winnerId }
            if (participant != null) {
                ranked.add(
                    KnockoutResultEntry(
                        rank = rank,
                        participantId = match.winnerId!!,
                        firstName = participant.firstName,
                        lastName = participant.lastName,
                        startNumber = participant.startNumber,
                        club = participant.club,
                    ),
                )
                rank++
            }
        }
        return ranked
    }
}
