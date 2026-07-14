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
