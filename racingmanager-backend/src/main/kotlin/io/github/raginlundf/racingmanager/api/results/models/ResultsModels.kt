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
