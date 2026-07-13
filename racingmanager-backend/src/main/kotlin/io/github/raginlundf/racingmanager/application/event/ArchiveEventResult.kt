package io.github.raginlundf.racingmanager.application.event

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus

sealed interface ArchiveEventResult {
    data class Success(val event: EventEntity) : ArchiveEventResult
    data object NotFound : ArchiveEventResult
    data class InvalidStatus(val current: EventStatus) : ArchiveEventResult
}
