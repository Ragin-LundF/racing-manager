package io.github.raginlundf.racingmanager.application.diagnostics

import java.util.UUID

data class RecoveryAction(
    val heatId: UUID,
    val action: String,
)
