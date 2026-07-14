package io.github.raginlundf.racingmanager.application.auth

import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.domain.auth.RefreshTokenEntity
import io.github.raginlundf.racingmanager.domain.tenant.MembershipEntity
import io.github.raginlundf.racingmanager.domain.tenant.MembershipStatus
import io.github.raginlundf.racingmanager.domain.tenant.TenantEntity
import io.github.raginlundf.racingmanager.domain.tenant.TenantStatus
import io.github.raginlundf.racingmanager.domain.user.UserEntity
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.MembershipRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.RefreshTokenRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.TenantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import java.util.UUID

class AuthService(
    private val userRepository: UserRepository,
    private val tenantRepository: TenantRepository,
    private val membershipRepository: MembershipRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val auditRepository: AuditRepository,
    private val passwordHasher: PasswordHasher,
    private val jwtService: JwtService,
    private val accessTokenTtl: Duration = 15.minutes,
    private val refreshTokenTtl: Duration = 30.days,
) {
    private val clock: Clock = Clock.System

    fun isFirstRun(): Boolean {
        return userRepository.count() == 0L
    }

    /** Local/offline first-run bootstrap: creates the implicit local tenant
        (idempotent — safe even if it already exists) together with its first
        administrator. Multi-tenant hosted registration is a separate, later
        flow; this one always lands the new admin in the local tenant. */
    fun setupAdmin(
        username: String,
        password: String,
        displayName: String,
    ): SetupResult {
        if (!isFirstRun()) return SetupResult.AlreadySetup

        val tenant = ensureReservedTenant(LOCAL_TENANT_ID, LOCAL_TENANT_SLUG, "Local")
        val now = clock.now()
        val passwordHash = passwordHasher.hash(password)
        val user = UserEntity(
            id = UUID.randomUUID(),
            tenantId = tenant.id,
            username = username,
            passwordHash = passwordHash,
            displayName = displayName,
            role = UserRole.ADMIN,
            createdAt = now,
        )
        userRepository.insert(user)
        membershipRepository.insert(
            MembershipEntity(
                id = UUID.randomUUID(),
                userId = user.id,
                tenantId = tenant.id,
                role = UserRole.ADMIN,
                createdAt = now,
            ),
        )
        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = user.id,
                action = "SETUP_COMPLETED",
                targetType = "User",
                targetId = user.id,
                summary = "First-run setup completed by ${user.username}",
                occurredAt = now,
            ),
        )
        return SetupResult.Success(user)
    }

    /** Username is unique only within a tenant, so a plain username may match
        more than one account across tenants. Pass [tenantSlug] to disambiguate
        (required once a username collides across tenants); without it, login
        only succeeds if the username happens to be unambiguous — it never
        guesses which account was meant. */
    fun login(
        username: String,
        password: String,
        tenantSlug: String? = null,
        correlationId: String? = null,
    ): LoginResult {
        val user = if (tenantSlug != null) {
            val tenant = tenantRepository.findBySlug(tenantSlug) ?: return LoginResult.InvalidCredentials
            userRepository.findByTenantAndUsername(tenant.id, username) ?: return LoginResult.InvalidCredentials
        } else {
            userRepository.findAllByUsername(username).singleOrNull() ?: return LoginResult.InvalidCredentials
        }

        if (!passwordHasher.verify(password, user.passwordHash)) {
            auditRepository.insert(
                AuditEntryEntity(
                    id = UUID.randomUUID(),
                    actorId = user.id,
                    action = "LOGIN_FAILED",
                    targetType = "User",
                    targetId = user.id,
                    correlationId = correlationId,
                    occurredAt = clock.now(),
                ),
            )
            return LoginResult.InvalidCredentials
        }

        val membership = membershipRepository.findByUserAndTenant(user.id, user.tenantId)
        if (membership == null || membership.status != MembershipStatus.ACTIVE) {
            return LoginResult.InvalidCredentials
        }

        val tenant = tenantRepository.findById(user.tenantId)
        if (tenant == null || tenant.status != TenantStatus.ACTIVE) {
            return LoginResult.TenantDisabled
        }

        val (accessToken, refreshToken, scopes) = issueTokens(user)
        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = user.id,
                action = "LOGIN_SUCCESS",
                targetType = "User",
                targetId = user.id,
                correlationId = correlationId,
                occurredAt = clock.now(),
            ),
        )
        return LoginResult.Success(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = accessTokenTtl.inWholeSeconds,
            tenantId = user.tenantId,
            scopes = scopes,
            user = user,
        )
    }

    /** Public hosted-mode tenant registration (design §4): creates a new
        tenant and its first user atomically, as tenant `rm:admin`. Distinct
        from [setupAdmin], which always lands in the fixed local tenant — the
        route layer gates this to [io.github.raginlundf.racingmanager.infrastructure.DeploymentMode.HOSTED]. */
    fun register(
        tenantDisplayName: String,
        tenantSlug: String,
        username: String,
        password: String,
        displayName: String,
    ): RegisterResult {
        if (tenantRepository.findBySlug(tenantSlug) != null) return RegisterResult.SlugTaken

        val now = clock.now()
        val tenant = TenantEntity(
            id = UUID.randomUUID(),
            slug = tenantSlug,
            displayName = tenantDisplayName,
            status = TenantStatus.ACTIVE,
            createdAt = now,
        )
        tenantRepository.insert(tenant)

        val user = UserEntity(
            id = UUID.randomUUID(),
            tenantId = tenant.id,
            username = username,
            passwordHash = passwordHasher.hash(password),
            displayName = displayName,
            role = UserRole.ADMIN,
            createdAt = now,
        )
        userRepository.insert(user)
        membershipRepository.insert(
            MembershipEntity(
                id = UUID.randomUUID(),
                userId = user.id,
                tenantId = tenant.id,
                role = UserRole.ADMIN,
                createdAt = now,
            ),
        )
        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = user.id,
                action = "TENANT_REGISTERED",
                targetType = "Tenant",
                targetId = tenant.id,
                summary = "Tenant '${tenant.displayName}' registered by ${user.username}",
                occurredAt = now,
            ),
        )

        val (accessToken, refreshToken, scopes) = issueTokens(user)
        return RegisterResult.Success(
            tenant = tenant,
            user = user,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = accessTokenTtl.inWholeSeconds,
            scopes = scopes,
        )
    }

    /** Tenant-admin-managed user creation (design §4): a normal user can never
        reach this — it is gated to `rm:admin` at the route layer — and cannot
        grant a role beyond what the caller (also `rm:admin`) explicitly
        requests, so a `DIRECTOR` cannot self-escalate by calling this. */
    fun createTenantUser(
        tenantId: UUID,
        username: String,
        password: String,
        displayName: String,
        role: UserRole = UserRole.DIRECTOR,
    ): CreateTenantUserResult {
        if (userRepository.findByTenantAndUsername(tenantId, username) != null) {
            return CreateTenantUserResult.UsernameTaken
        }

        val now = clock.now()
        val user = UserEntity(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            username = username,
            passwordHash = passwordHasher.hash(password),
            displayName = displayName,
            role = role,
            createdAt = now,
        )
        userRepository.insert(user)
        membershipRepository.insert(
            MembershipEntity(
                id = UUID.randomUUID(),
                userId = user.id,
                tenantId = tenantId,
                role = role,
                createdAt = now,
            ),
        )
        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = user.id,
                action = "TENANT_USER_CREATED",
                targetType = "User",
                targetId = user.id,
                summary = "User '${user.username}' created",
                occurredAt = now,
            ),
        )
        return CreateTenantUserResult.Success(user)
    }

    fun getTenant(tenantId: UUID): TenantEntity? {
        return tenantRepository.findById(tenantId)
    }

    fun updateTenant(tenantId: UUID, displayName: String, settings: String?): TenantEntity? {
        val tenant = tenantRepository.findById(tenantId) ?: return null
        val updated = tenant.copy(displayName = displayName, settings = settings, updatedAt = clock.now())
        tenantRepository.update(updated)
        return updated
    }

    /** One row per user in the tenant, paired with their membership (role +
        status) — the two are still separate tables even though today's
        single-tenant-per-user reality keeps them in lockstep, because the
        domain model must not assume that stays true (design §3). */
    fun listTenantUsers(tenantId: UUID): List<TenantMember> {
        val memberships = membershipRepository.findByTenantId(tenantId).associateBy { it.userId }
        return userRepository.findByTenantId(tenantId).mapNotNull { user ->
            memberships[user.id]?.let { membership -> TenantMember(user, membership) }
        }
    }

    /** Updates a tenant member's role and/or membership status. Keeps
        [UserEntity.role] (used to compute scopes at login) and the
        membership's role in lockstep — see [listTenantUsers]. */
    fun updateTenantUser(
        tenantId: UUID,
        userId: UUID,
        role: UserRole? = null,
        status: MembershipStatus? = null,
    ): UpdateTenantUserResult {
        val user = userRepository.findById(userId)?.takeIf { it.tenantId == tenantId }
            ?: return UpdateTenantUserResult.NotFound
        val membership = membershipRepository.findByUserAndTenant(userId, tenantId)
            ?: return UpdateTenantUserResult.NotFound

        val newRole = role ?: membership.role
        val newStatus = status ?: membership.status
        val now = clock.now()
        if (role != null) userRepository.updateRole(userId, role)
        membershipRepository.updateRoleAndStatus(userId, tenantId, newRole, newStatus, now)
        if (newStatus != MembershipStatus.ACTIVE) {
            refreshTokenRepository.revokeAllForUser(userId)
        }

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = userId,
                action = "TENANT_USER_UPDATED",
                targetType = "User",
                targetId = userId,
                summary = "User '${user.username}' updated (role=$newRole, status=$newStatus)",
                occurredAt = now,
            ),
        )
        return UpdateTenantUserResult.Success(
            user.copy(role = newRole),
            membership.copy(role = newRole, status = newStatus, updatedAt = now),
        )
    }

    /** Issues a new access token from a still-valid refresh token. The refresh
        token itself is not rotated on use — ponytail: simplest thing that
        works, rotate-on-use if replay detection is ever required. */
    fun refresh(refreshToken: String): RefreshResult {
        val id = runCatching { UUID.fromString(refreshToken) }.getOrNull()
            ?: return RefreshResult.Invalid
        val stored = refreshTokenRepository.findById(id) ?: return RefreshResult.Invalid
        val user = userRepository.findById(stored.userId) ?: return RefreshResult.Invalid
        if (!stored.isValid(clock.now(), user.tokenVersion)) return RefreshResult.Invalid

        val scopes = setOf(scopeForRole(user.role))
        val accessToken = jwtService.issueAccessToken(user.id, stored.tenantId, scopes, ttl = accessTokenTtl)
        return RefreshResult.Success(accessToken = accessToken, expiresInSeconds = accessTokenTtl.inWholeSeconds)
    }

    fun logout(refreshToken: String?, correlationId: String? = null) {
        val id = refreshToken?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        val stored = refreshTokenRepository.findById(id) ?: return
        refreshTokenRepository.revoke(id)
        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = stored.userId,
                action = "LOGOUT",
                targetType = "User",
                targetId = stored.userId,
                correlationId = correlationId,
                occurredAt = clock.now(),
            ),
        )
    }

    fun currentUser(userId: UUID): UserEntity? {
        return userRepository.findById(userId)
    }

    fun changePassword(
        userId: UUID,
        currentPassword: String,
        newPassword: String,
    ): ChangePasswordResult {
        val user = userRepository.findById(userId)
            ?: return ChangePasswordResult.UserNotFound

        if (!passwordHasher.verify(currentPassword, user.passwordHash)) {
            return ChangePasswordResult.InvalidCurrentPassword
        }

        val newHash = passwordHasher.hash(newPassword)
        userRepository.updatePassword(id = userId, newHash = newHash)
        userRepository.incrementTokenVersion(userId)
        refreshTokenRepository.revokeAllForUser(userId)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = userId,
                action = "PASSWORD_CHANGED",
                targetType = "User",
                targetId = userId,
                occurredAt = clock.now(),
            ),
        )
        return ChangePasswordResult.Success
    }

    /** Every existing supervisor lives in the reserved platform tenant by
        construction (see [PLATFORM_TENANT_ID]), so "no supervisor yet" is
        simply "the platform tenant has no members". */
    fun isFirstSupervisorRun(): Boolean {
        return userRepository.findByTenantId(PLATFORM_TENANT_ID).isEmpty()
    }

    /** One-time hosted-platform bootstrap, mirroring [setupAdmin]'s shape:
        only succeeds once, creating the reserved platform tenant (idempotent)
        and its first `rm:supervisor` user. There is no self-registration path
        for supervisors — this is the only way one is ever created. */
    fun setupSupervisor(username: String, password: String, displayName: String): SetupResult {
        if (!isFirstSupervisorRun()) return SetupResult.AlreadySetup

        val tenant = ensureReservedTenant(PLATFORM_TENANT_ID, PLATFORM_TENANT_SLUG, "Platform")
        val now = clock.now()
        val user = UserEntity(
            id = UUID.randomUUID(),
            tenantId = tenant.id,
            username = username,
            passwordHash = passwordHasher.hash(password),
            displayName = displayName,
            role = UserRole.SUPERVISOR,
            createdAt = now,
        )
        userRepository.insert(user)
        membershipRepository.insert(
            MembershipEntity(
                id = UUID.randomUUID(),
                userId = user.id,
                tenantId = tenant.id,
                role = UserRole.SUPERVISOR,
                createdAt = now,
            ),
        )
        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = user.id,
                action = "SUPERVISOR_SETUP_COMPLETED",
                targetType = "User",
                targetId = user.id,
                summary = "Supervisor setup completed by ${user.username}",
                occurredAt = now,
            ),
        )
        return SetupResult.Success(user)
    }

    /** Tenant metadata only (design §3: a supervisor must not gain ordinary
        access to tenant race data) — every tenant, not just the caller's own. */
    fun listAllTenants(): List<TenantEntity> {
        return tenantRepository.findAll().filter { it.id != PLATFORM_TENANT_ID }
    }

    fun deactivateTenant(tenantId: UUID, supervisorId: UUID): TenantEntity? {
        val tenant = tenantRepository.findById(tenantId) ?: return null
        val updated = tenant.copy(status = TenantStatus.DISABLED, updatedAt = clock.now())
        tenantRepository.update(updated)
        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = supervisorId,
                action = "TENANT_DEACTIVATED",
                targetType = "Tenant",
                targetId = tenant.id,
                summary = "Tenant '${tenant.displayName}' deactivated",
                occurredAt = clock.now(),
            ),
        )
        return updated
    }

    /** Soft-delete only: marks the tenant [TenantStatus.PENDING_DELETION] and
        audits the request. No purge job exists yet — an actual data-removal
        worker respecting the retention window is explicitly out of scope for
        this slice (design §5/§9 non-goals). [confirmSlug] must match the
        tenant's slug so a supervisor cannot delete a tenant by ID alone. */
    fun requestTenantDeletion(tenantId: UUID, confirmSlug: String, supervisorId: UUID): DeleteTenantResult {
        val tenant = tenantRepository.findById(tenantId) ?: return DeleteTenantResult.NotFound
        if (tenant.slug != confirmSlug) return DeleteTenantResult.ConfirmationMismatch

        val updated = tenant.copy(status = TenantStatus.PENDING_DELETION, updatedAt = clock.now())
        tenantRepository.update(updated)
        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = supervisorId,
                action = "TENANT_DELETION_REQUESTED",
                targetType = "Tenant",
                targetId = tenant.id,
                summary = "Tenant '${tenant.displayName}' marked for deletion",
                occurredAt = clock.now(),
            ),
        )
        return DeleteTenantResult.Success(updated)
    }

    /** Idempotently returns the reserved tenant with the given fixed identity,
        creating it on first use. Shared by [setupAdmin] (local tenant) and
        [setupSupervisor] (platform tenant) — both reserved tenants use the
        same fixed-id/find-or-create shape. */
    private fun ensureReservedTenant(id: UUID, slug: String, displayName: String): TenantEntity {
        return tenantRepository.findById(id) ?: TenantEntity(
            id = id,
            slug = slug,
            displayName = displayName,
            status = TenantStatus.ACTIVE,
            createdAt = clock.now(),
        ).also { tenantRepository.insert(it) }
    }

    /** Issues an access token plus a fresh persisted refresh token for [user].
        Shared by [login] and [register] so both hand back the exact same
        token shape. */
    private fun issueTokens(user: UserEntity): Triple<String, String, Set<String>> {
        val now = clock.now()
        val scopes = setOf(scopeForRole(user.role))
        val accessToken = jwtService.issueAccessToken(user.id, user.tenantId, scopes, ttl = accessTokenTtl)
        val refreshTokenId = UUID.randomUUID()
        refreshTokenRepository.insert(
            RefreshTokenEntity(
                id = refreshTokenId,
                userId = user.id,
                tenantId = user.tenantId,
                tokenVersion = user.tokenVersion,
                createdAt = now,
                expiresAt = now.plus(refreshTokenTtl),
            ),
        )
        return Triple(accessToken, refreshTokenId.toString(), scopes)
    }

    companion object {
        /** Fixed, stable identity for the implicit local/offline tenant — never
            randomly generated, so every local installation converges on the
            same tenant id. */
        val LOCAL_TENANT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        const val LOCAL_TENANT_SLUG = "local"

        /** Reserved placeholder tenant every `rm:supervisor` user's `tenantId`
            points to — not a real racing tenant, never returned by
            [listAllTenants] or any tenant-facing API. */
        val PLATFORM_TENANT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        const val PLATFORM_TENANT_SLUG = "platform"
    }
}

