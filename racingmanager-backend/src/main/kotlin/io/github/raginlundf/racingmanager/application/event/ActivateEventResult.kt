package io.github.raginlundf.racingmanager.application.event

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus

sealed interface ActivateEventResult {
    data class Success(val event: EventEntity) : ActivateEventResult
    data object NotFound : ActivateEventResult
    data class InvalidStatus(val current: EventStatus) : ActivateEventResult
    data class Conflict(val expected: Long, val actual: Long) : ActivateEventResult
}
