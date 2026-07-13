package io.github.raginlundf.racingmanager.application.event

sealed interface DeleteEventResult {
    data object Success : DeleteEventResult
    data object NotFound : DeleteEventResult
}
