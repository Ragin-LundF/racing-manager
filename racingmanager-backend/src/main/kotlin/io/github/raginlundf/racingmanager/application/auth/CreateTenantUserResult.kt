package io.github.raginlundf.racingmanager.application.auth

import io.github.raginlundf.racingmanager.domain.user.UserEntity

sealed interface CreateTenantUserResult {
    data class Success(val user: UserEntity) : CreateTenantUserResult
    data object UsernameTaken : CreateTenantUserResult
}
