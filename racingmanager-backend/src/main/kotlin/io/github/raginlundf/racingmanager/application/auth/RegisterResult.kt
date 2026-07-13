package io.github.raginlundf.racingmanager.application.auth

import io.github.raginlundf.racingmanager.domain.tenant.TenantEntity
import io.github.raginlundf.racingmanager.domain.user.UserEntity

sealed interface RegisterResult {
    data class Success(
        val tenant: TenantEntity,
        val user: UserEntity,
        val accessToken: String,
        val refreshToken: String,
        val expiresInSeconds: Long,
        val scopes: Set<String>,
    ) : RegisterResult
    data object SlugTaken : RegisterResult
}
