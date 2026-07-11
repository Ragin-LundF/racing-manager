package io.github.raginlundf.racingmanager.domain.session

import kotlin.time.Instant
import java.util.UUID

data class SessionEntity(
    val id: UUID,
    val userId: UUID,
    val createdAt: Instant,
    val expiresAt: Instant,
    val lastAccessedAt: Instant,
) {
    fun isExpired(now: Instant): Boolean = now > expiresAt
}
