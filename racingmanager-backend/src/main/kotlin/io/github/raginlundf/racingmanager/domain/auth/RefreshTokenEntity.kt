package io.github.raginlundf.racingmanager.domain.auth

import kotlin.time.Instant
import java.util.UUID

/** An opaque refresh token, presented by the client as [id] — the same
    DB-backed-opaque-token shape the removed session mechanism used, reused
    here because refresh happens rarely (unlike access tokens, which must stay
    stateless JWTs). [tokenVersion] captures the user's `token_version` at
    issuance time; a later mismatch (bumped by password change or an explicit
    "logout everywhere") invalidates the refresh token without needing to
    enumerate and delete every outstanding token. */
data class RefreshTokenEntity(
    val id: UUID,
    val userId: UUID,
    val tenantId: UUID,
    val tokenVersion: Int,
    val createdAt: Instant,
    val expiresAt: Instant,
    val revoked: Boolean = false,
) {
    fun isValid(now: Instant, currentTokenVersion: Int): Boolean {
        return !revoked && now <= expiresAt && tokenVersion == currentTokenVersion
    }
}
