package io.github.raginlundf.racingmanager.application.results

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus

sealed interface RestoreResult {
    data class Success(val event: EventEntity) : RestoreResult
    data object NotFound : RestoreResult
    data class InvalidStatus(val status: EventStatus) : RestoreResult
    data object SnapshotMismatch : RestoreResult
}
