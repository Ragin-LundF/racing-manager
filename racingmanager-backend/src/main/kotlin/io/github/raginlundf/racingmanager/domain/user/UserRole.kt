package io.github.raginlundf.racingmanager.domain.user

enum class UserRole {
    ADMIN,
    DIRECTOR,
    /** Hosted platform operator (`rm:supervisor`) — not a tenant role. Lives in
        the reserved platform tenant (see `AuthService.PLATFORM_TENANT_ID`),
        not a real racing tenant; a `Membership` row still exists for schema
        consistency (every user has exactly one required `tenantId`). */
    SUPERVISOR,
}
