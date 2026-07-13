package io.github.raginlundf.racingmanager.domain.user

import kotlin.time.Instant
import java.util.UUID

data class UserEntity(
    val id: UUID,
    val tenantId: UUID,
    val username: String,
    val passwordHash: String,
    val displayName: String,
    val role: UserRole,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val email: String? = null,
    /** Bumped on password change or an explicit "logout everywhere"; a refresh
        token issued under an older version is rejected. */
    val tokenVersion: Int = 0,
)
