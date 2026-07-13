package io.github.raginlundf.racingmanager.application.auth

import io.github.raginlundf.racingmanager.domain.tenant.MembershipEntity
import io.github.raginlundf.racingmanager.domain.user.UserEntity

data class TenantMember(val user: UserEntity, val membership: MembershipEntity)
