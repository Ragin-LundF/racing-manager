package io.github.raginlundf.racingmanager.application.sync

sealed interface RevokeResult {
    data object Success : RevokeResult
    data object NotFound : RevokeResult
}
