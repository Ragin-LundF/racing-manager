package io.github.raginlundf.racingmanager.application.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import java.util.UUID

class RequestPrincipalTest {

    private fun principalWith(scopes: Set<String>) = RequestPrincipal(
        userId = UUID.randomUUID(),
        tenantId = UUID.randomUUID(),
        scopes = scopes,
        jti = UUID.randomUUID().toString(),
        expiresAt = Clock.System.now(),
    )

    @Test
    fun `rm-admin satisfies a route that only requires rm-user`() {
        val principal = principalWith(setOf(Scopes.ADMIN))

        assertTrue(principal.hasAnyScope(Scopes.USER))
    }

    @Test
    fun `rm-user does not satisfy a route that requires rm-admin`() {
        val principal = principalWith(setOf(Scopes.USER))

        assertFalse(principal.hasAnyScope(Scopes.ADMIN))
    }

    @Test
    fun `rm-spectator is never additive to management scopes`() {
        val principal = principalWith(setOf(Scopes.SPECTATOR))

        assertFalse(principal.hasAnyScope(Scopes.ADMIN))
        assertFalse(principal.hasAnyScope(Scopes.USER))
    }

    @Test
    fun `hasAnyScope matches if any of several required scopes is present`() {
        val principal = principalWith(setOf(Scopes.USER))

        assertTrue(principal.hasAnyScope(Scopes.ADMIN, Scopes.USER))
    }

    @Test
    fun `scopeForRole maps ADMIN and DIRECTOR correctly`() {
        assertTrue(scopeForRole(io.github.raginlundf.racingmanager.domain.user.UserRole.ADMIN) == Scopes.ADMIN)
        assertTrue(scopeForRole(io.github.raginlundf.racingmanager.domain.user.UserRole.DIRECTOR) == Scopes.USER)
    }
}
