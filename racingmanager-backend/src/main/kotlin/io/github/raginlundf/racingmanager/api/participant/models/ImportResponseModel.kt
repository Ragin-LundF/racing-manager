package io.github.raginlundf.racingmanager.api.participant.models

import kotlinx.serialization.Serializable

@Serializable
data class ImportResponseModel(
    val created: Int,
    val errors: List<ImportErrorModel>,
)
