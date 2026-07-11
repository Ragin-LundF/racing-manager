package io.github.raginlundf.racingmanager.domain.user

import kotlin.time.Instant
import java.util.UUID

data class UserEntity(
    val id: UUID,
    val username: String,
    val passwordHash: String,
    val displayName: String,
    val role: UserRole,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
)
