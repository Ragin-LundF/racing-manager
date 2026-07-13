package io.github.raginlundf.racingmanager.application.event

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus

sealed interface ReactivateEventResult {
    data class Success(val event: EventEntity) : ReactivateEventResult
    data object NotFound : ReactivateEventResult
    data class InvalidStatus(val current: EventStatus) : ReactivateEventResult
}
