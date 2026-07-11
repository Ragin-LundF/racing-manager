package io.github.raginlundf.racingmanager.api.qualification.models

import kotlinx.serialization.Serializable

@Serializable
data class QualificationResponseModel(
    val id: String,
    val eventId: String,
    val status: String,
    val numberOfRuns: Int,
    val seed: Long,
    val createdAt: String,
    val updatedAt: String? = null,
    val finalizedAt: String? = null,
    val finalizedBy: String? = null,
)

@Serializable
data class SetupQualificationRequestModel(
    val numberOfRuns: Int = 2,
)

@Serializable
data class QualificationRankingResponseModel(
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
data class QualificationProgressResponseModel(
    val status: String,
    val totalHeats: Int,
    val completedHeats: Int,
    val inProgressHeats: Int,
    val plannedHeats: Int,
    val cancelledHeats: Int,
    val totalParticipants: Int,
    val participantsWithResults: Int,
)

@Serializable
data class HeatScheduleResponseModel(
    val id: String,
    val eventId: String,
    val round: Int,
    val heatNumber: Int,
    val status: String,
    val lanes: List<HeatLaneScheduleResponseModel>,
    val measurements: List<MeasurementScheduleResponseModel>,
    val createdAt: String,
    val armedAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
)

@Serializable
data class HeatLaneScheduleResponseModel(
    val lane: Int,
    val participantId: String,
    val participantStartNumber: Int,
    val participantFirstName: String,
    val participantLastName: String,
)

@Serializable
data class MeasurementScheduleResponseModel(
    val id: String,
    val heatId: String,
    val lane: Int,
    val durationNanos: Long,
    val outcome: String,
    val receivedAt: String,
)
