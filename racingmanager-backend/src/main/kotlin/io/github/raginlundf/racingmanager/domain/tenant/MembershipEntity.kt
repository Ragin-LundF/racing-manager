package io.github.raginlundf.racingmanager.domain.tenant

import io.github.raginlundf.racingmanager.domain.user.UserRole
import kotlin.time.Instant
import java.util.UUID

/** Connects a user to a tenant with an effective tenant-scoped [role]. A user
    may hold at most one membership per tenant, but may belong to more than
    one tenant — the JVM/DB model must not assume a single-tenant user even
    though the first implementation issues single-tenant tokens. */
data class MembershipEntity(
    val id: UUID,
    val userId: UUID,
    val tenantId: UUID,
    val status: MembershipStatus = MembershipStatus.ACTIVE,
    val role: UserRole,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
)
