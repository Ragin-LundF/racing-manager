package io.github.raginlundf.racingmanager.application.auth

sealed interface ChangePasswordResult {
    data object Success : ChangePasswordResult
    data object UserNotFound : ChangePasswordResult
    data object InvalidCurrentPassword : ChangePasswordResult
}
