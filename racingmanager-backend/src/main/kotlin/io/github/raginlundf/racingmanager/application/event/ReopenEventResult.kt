package io.github.raginlundf.racingmanager.application.event

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus

sealed interface ReopenEventResult {
    data class Success(val event: EventEntity) : ReopenEventResult
    data object NotFound : ReopenEventResult
    data class InvalidStatus(val status: EventStatus) : ReopenEventResult
}
