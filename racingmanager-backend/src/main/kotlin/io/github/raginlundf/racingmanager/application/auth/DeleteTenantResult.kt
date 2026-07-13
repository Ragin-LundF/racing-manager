package io.github.raginlundf.racingmanager.application.auth

import io.github.raginlundf.racingmanager.domain.tenant.TenantEntity

sealed interface DeleteTenantResult {
    data class Success(val tenant: TenantEntity) : DeleteTenantResult
    data object NotFound : DeleteTenantResult
    data object ConfirmationMismatch : DeleteTenantResult
}
