package io.github.raginlundf.racingmanager.domain.tenant

enum class TenantStatus {
    ACTIVE,
    DISABLED,
    /** Soft-deleted by a supervisor; eligible for purge after a retention
        window measured from `updatedAt`. No purge job exists yet — this
        marks intent and blocks access, it does not itself delete data. */
    PENDING_DELETION,
}
