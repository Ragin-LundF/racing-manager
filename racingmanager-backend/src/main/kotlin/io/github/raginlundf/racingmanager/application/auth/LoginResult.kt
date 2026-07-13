package io.github.raginlundf.racingmanager.application.auth

import io.github.raginlundf.racingmanager.domain.user.UserEntity
import java.util.UUID

sealed interface LoginResult {
    data class Success(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSeconds: Long,
        val tenantId: UUID,
        val scopes: Set<String>,
        val user: UserEntity,
    ) : LoginResult
    data object InvalidCredentials : LoginResult
    data object TenantDisabled : LoginResult
}
