package io.github.raginlundf.racingmanager.api.event.models

import kotlinx.serialization.Serializable

@Serializable
data class ConflictResponseModel(
    val code: String,
    val message: String,
    val expectedVersion: Long,
    val actualVersion: Long,
)
