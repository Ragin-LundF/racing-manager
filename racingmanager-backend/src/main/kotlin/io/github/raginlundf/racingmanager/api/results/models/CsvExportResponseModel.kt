package io.github.raginlundf.racingmanager.api.results.models

import kotlinx.serialization.Serializable

@Serializable
data class CsvExportResponseModel(
    val csv: String,
    val filename: String,
)
