package io.github.raginlundf.racingmanager.api.participant.models

import kotlinx.serialization.Serializable

@Serializable
data class ImportErrorModel(
    val rowIndex: Int,
    val message: String,
)
