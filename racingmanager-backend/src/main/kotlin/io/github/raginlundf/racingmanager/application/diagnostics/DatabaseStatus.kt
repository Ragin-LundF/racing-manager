package io.github.raginlundf.racingmanager.application.diagnostics

data class DatabaseStatus(
    val connected: Boolean,
    val pingMs: Long,
)
