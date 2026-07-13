package io.github.raginlundf.racingmanager.application.auth

import io.github.raginlundf.racingmanager.domain.user.UserRole

/** The four coarse-grained capability scopes carried in access tokens (design §3).
    `rm:admin` implies the operational rights of `rm:user`; `rm:spectator` is never
    additive to either management scope. The scope→role mapping lives only here. */
object Scopes {
    /** Hosted platform scope for cross-tenant tenant lifecycle management. Not a
        tenant role — does not grant ordinary access to tenant race data. */
    const val SUPERVISOR = "rm:supervisor"

    /** Full rights within the token's tenant, including tenant self-service.
        Maps from [UserRole.ADMIN]. */
    const val ADMIN = "rm:admin"

    /** Operational rights within the token's tenant (events, participants, heats,
        qualifications, knockouts, results); no tenant self-service. Maps from
        [UserRole.DIRECTOR]. */
    const val USER = "rm:user"

    /** Read-only access to exactly one event, selected when the token is issued. */
    const val SPECTATOR = "rm:spectator"
}

fun scopeForRole(role: UserRole): String {
    return when (role) {
        UserRole.ADMIN -> Scopes.ADMIN
        UserRole.DIRECTOR -> Scopes.USER
        UserRole.SUPERVISOR -> Scopes.SUPERVISOR
    }
}
