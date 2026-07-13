package io.github.raginlundf.racingmanager.application.auth

import kotlin.time.Instant
import java.util.UUID

/** The single request principal resolved from a verified JWT access token
    (design §7). Route handlers declare required capability by checking
    [scopes] and comparing [tenantId] against the resource being accessed;
    [eventId] is present only for `rm:spectator` tokens. */
data class RequestPrincipal(
    val userId: UUID,
    val tenantId: UUID,
    val scopes: Set<String>,
    val eventId: UUID? = null,
    val jti: String,
    val expiresAt: Instant,
) {
    /** `rm:admin` implies every operational right of `rm:user` (design §3); a
        route declaring `Scopes.USER` as sufficient must also accept a token
        scoped `rm:admin`. `rm:spectator` is never additive to either. */
    fun hasAnyScope(vararg required: String): Boolean {
        val effective = if (Scopes.ADMIN in scopes) scopes + Scopes.USER else scopes
        return required.any { it in effective }
    }
}
