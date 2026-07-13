package io.github.raginlundf.racingmanager.application.participant

data class ImportRowError(
    val rowIndex: Int,
    val message: String,
)
