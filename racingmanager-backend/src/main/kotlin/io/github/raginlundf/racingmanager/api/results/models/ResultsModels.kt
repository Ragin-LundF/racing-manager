package io.github.raginlundf.racingmanager.api.results.models

import kotlinx.serialization.Serializable

@Serializable
data class EventResultSnapshotResponseModel(
    val event: EventResultSummaryModel,
    val qualificationRankings: List<QualificationResultEntryModel>,
    val knockoutResults: List<KnockoutResultEntryModel>,
    val allHeats: List<HeatResultEntryModel>,
    val measurementType: String,
    val isSimulated: Boolean,
)

@Serializable
data class EventResultSummaryModel(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String,
    val laneType: String,
    val measurementType: String,
    val createdAt: String,
    val activatedAt: String? = null,
    val completedAt: String? = null,
)

@Serializable
data class QualificationResultEntryModel(
    val participantId: String,
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
    val bestTimeNanos: Long? = null,
    val totalTimeNanos: Long? = null,
    val completedRuns: Int,
    val dnfCount: Int,
    val rank: Int,
)

@Serializable
data class KnockoutResultEntryModel(
    val rank: Int,
    val participantId: String,
    val firstName: String,
    val lastName: String,
    val startNumber: Int,
    val club: String? = null,
)

@Serializable
data class HeatResultEntryModel(
    val id: String,
    val round: Int,
    val heatNumber: Int,
    val status: String,
    val lanes: List<HeatResultLaneModel>,
    val measurements: List<HeatResultMeasurementModel>,
    val startedAt: String? = null,
    val finishedAt: String? = null,
)

@Serializable
data class HeatResultLaneModel(
    val lane: Int,
    val participantId: String,
    val participantStartNumber: Int,
    val participantFirstName: String,
    val participantLastName: String,
)

@Serializable
data class HeatResultMeasurementModel(
    val id: String,
    val lane: Int,
    val durationNanos: Long,
    val outcome: String,
    val receivedAt: String,
)

@Serializable
data class CsvExportResponseModel(
    val csv: String,
    val filename: String,
)

@Serializable
data class JsonExportResponseModel(
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val event: EventResultSnapshotResponseModel,
)

@Serializable
data class HtmlReportResponseModel(
    val html: String,
    val filename: String,
)

@Serializable
data class BackupResponseModel(
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val event: EventResultSnapshotResponseModel,
)

@Serializable
data class RestoreResponseModel(
    val eventId: String,
    val name: String,
    val status: String,
)
