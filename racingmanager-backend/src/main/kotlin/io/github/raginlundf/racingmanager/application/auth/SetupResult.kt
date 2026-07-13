package io.github.raginlundf.racingmanager.application.auth

import io.github.raginlundf.racingmanager.domain.user.UserEntity

sealed interface SetupResult {
    data class Success(val user: UserEntity) : SetupResult
    data object AlreadySetup : SetupResult
}
