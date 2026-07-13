package io.github.raginlundf.racingmanager.api.participant.models

import kotlinx.serialization.Serializable

@Serializable
data class ImportCsvRequestModel(
    val rows: List<CsvRowModel>,
)
