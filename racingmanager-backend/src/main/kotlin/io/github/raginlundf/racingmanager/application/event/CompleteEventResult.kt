package io.github.raginlundf.racingmanager.application.event

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus

sealed interface CompleteEventResult {
    data class Success(val event: EventEntity) : CompleteEventResult
    data object NotFound : CompleteEventResult
    data class InvalidStatus(val status: EventStatus) : CompleteEventResult
}
