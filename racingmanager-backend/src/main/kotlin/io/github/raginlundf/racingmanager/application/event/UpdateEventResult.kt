package io.github.raginlundf.racingmanager.application.event

import io.github.raginlundf.racingmanager.domain.event.EventEntity

sealed interface UpdateEventResult {
    data class Success(val event: EventEntity) : UpdateEventResult
    data object NotFound : UpdateEventResult
    /** COMPLETED and ARCHIVED events are read-only; DRAFT and ACTIVE are editable
        so an operator can correct a running event's details. */
    data object CannotModifyFinishedEvent : UpdateEventResult
    data class Conflict(val expected: Long, val actual: Long) : UpdateEventResult

    /** Checked out to a local instance for offline execution (design §I.3) —
        rejected until the local instance syncs its results back. */
    data object Locked : UpdateEventResult
}
