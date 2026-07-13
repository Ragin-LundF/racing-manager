package io.github.raginlundf.racingmanager.application.auth

import io.github.raginlundf.racingmanager.domain.tenant.MembershipEntity
import io.github.raginlundf.racingmanager.domain.user.UserEntity

sealed interface UpdateTenantUserResult {
    data class Success(val user: UserEntity, val membership: MembershipEntity) : UpdateTenantUserResult
    data object NotFound : UpdateTenantUserResult
}
