package io.github.raginlundf.racingmanager.application.event

import io.github.raginlundf.racingmanager.domain.event.EventEntity

sealed interface CreateEventResult {
    data class Success(val event: EventEntity) : CreateEventResult
}
