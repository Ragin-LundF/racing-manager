package io.github.raginlundf.racingmanager.application.auth

import io.github.raginlundf.racingmanager.domain.tenant.TenantEntity
import io.github.raginlundf.racingmanager.domain.tenant.TenantStatus
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.MembershipRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.RefreshTokenRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.TenantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.security.LocalJwtKeyProvider
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import java.util.UUID

class AuthServiceTest {

    private val userRepository = UserRepository()
    private val tenantRepository = TenantRepository()
    private val membershipRepository = MembershipRepository()
    private val refreshTokenRepository = RefreshTokenRepository()
    private val auditRepository = AuditRepository()
    private val passwordHasher = PasswordHasher()
    private val jwtKeyProvider = LocalJwtKeyProvider(SigningKeyRepository())
    private val jwtService = JwtService(jwtKeyProvider)
    private val authService = AuthService(
        userRepository,
        tenantRepository,
        membershipRepository,
        refreshTokenRepository,
        auditRepository,
        passwordHasher,
        jwtService,
    )

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        jwtKeyProvider.ensureKeyExists()
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    @Test
    fun `isFirstRun returns true on empty database`() {
        assertTrue(authService.isFirstRun())
    }

    @Test
    fun `isFirstRun returns false after setup`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        assertEquals(false, authService.isFirstRun())
    }

    @Test
    fun `setupAdmin creates admin user in the local tenant and returns Success`() {
        val result = authService.setupAdmin("admin", "password123", "Admin User")

        val success = assertIs<SetupResult.Success>(result)
        assertEquals("admin", success.user.username)
        assertEquals("Admin User", success.user.displayName)
        assertEquals(UserRole.ADMIN, success.user.role)
        assertEquals(AuthService.LOCAL_TENANT_ID, success.user.tenantId)
        assertNotNull(success.user.id)

        val membership = membershipRepository.findByUserAndTenant(success.user.id, AuthService.LOCAL_TENANT_ID)
        assertNotNull(membership)
        assertEquals(UserRole.ADMIN, membership.role)
    }

    @Test
    fun `setupAdmin returns AlreadySetup on second call`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val result = authService.setupAdmin("admin2", "otherpass", "Other Admin")

        assertIs<SetupResult.AlreadySetup>(result)
    }

    @Test
    fun `login with valid credentials returns tokens`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val result = authService.login("admin", "password123")

        val success = assertIs<LoginResult.Success>(result)
        assertEquals("admin", success.user.username)
        assertEquals(AuthService.LOCAL_TENANT_ID, success.tenantId)
        assertEquals(setOf("rm:admin"), success.scopes)
        assertNotNull(success.accessToken)
        assertNotNull(success.refreshToken)
        assertTrue(success.expiresInSeconds > 0)
    }

    @Test
    fun `login with wrong password returns InvalidCredentials`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val result = authService.login("admin", "wrongpassword")

        assertIs<LoginResult.InvalidCredentials>(result)
    }

    @Test
    fun `login with unknown username returns InvalidCredentials`() {
        val result = authService.login("nonexistent", "password123")

        assertIs<LoginResult.InvalidCredentials>(result)
    }

    @Test
    fun `login for a deactivated tenant returns TenantDisabled`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        authService.deactivateTenant(AuthService.LOCAL_TENANT_ID, UUID.randomUUID())

        val result = authService.login("admin", "password123")

        assertIs<LoginResult.TenantDisabled>(result)
    }

    @Test
    fun `access token from login verifies to the correct principal`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val login = authService.login("admin", "password123") as LoginResult.Success

        val principal = jwtService.verifyAccessToken(login.accessToken)

        assertNotNull(principal)
        assertEquals(login.user.id, principal.userId)
        assertEquals(AuthService.LOCAL_TENANT_ID, principal.tenantId)
    }

    @Test
    fun `refresh with a valid refresh token issues a new access token`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val login = authService.login("admin", "password123") as LoginResult.Success

        val result = authService.refresh(login.refreshToken)

        val success = assertIs<RefreshResult.Success>(result)
        assertNotNull(jwtService.verifyAccessToken(success.accessToken))
    }

    @Test
    fun `refresh with an unknown token returns Invalid`() {
        val result = authService.refresh(UUID.randomUUID().toString())

        assertIs<RefreshResult.Invalid>(result)
    }

    @Test
    fun `refresh with a malformed token returns Invalid`() {
        val result = authService.refresh("not-a-uuid")

        assertIs<RefreshResult.Invalid>(result)
    }

    @Test
    fun `logout revokes the refresh token so it can no longer be refreshed`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val login = authService.login("admin", "password123") as LoginResult.Success

        authService.logout(login.refreshToken)

        assertIs<RefreshResult.Invalid>(authService.refresh(login.refreshToken))
    }

    @Test
    fun `changePassword with valid credentials succeeds`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val login = authService.login("admin", "password123") as LoginResult.Success

        val result = authService.changePassword(login.user.id, "password123", "newpassword")

        assertIs<ChangePasswordResult.Success>(result)
    }

    @Test
    fun `changePassword revokes outstanding refresh tokens`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val login = authService.login("admin", "password123") as LoginResult.Success

        authService.changePassword(login.user.id, "password123", "newpassword")

        assertIs<RefreshResult.Invalid>(authService.refresh(login.refreshToken))
    }

    @Test
    fun `changePassword with wrong current password fails`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val login = authService.login("admin", "password123") as LoginResult.Success

        val result = authService.changePassword(login.user.id, "wrongpassword", "newpassword")

        assertIs<ChangePasswordResult.InvalidCurrentPassword>(result)
    }

    @Test
    fun `changePassword for unknown user returns UserNotFound`() {
        val result = authService.changePassword(UUID.randomUUID(), "password123", "newpassword")

        assertIs<ChangePasswordResult.UserNotFound>(result)
    }

    @Test
    fun `currentUser returns null for unknown id`() {
        assertNull(authService.currentUser(UUID.randomUUID()))
    }

    /** Inserts a tenant with the given status and an `updatedAt` [ageHours] in the past. */
    private fun seedTenant(status: TenantStatus, ageHours: Int): UUID {
        val id = UUID.randomUUID()
        val now = Clock.System.now()
        tenantRepository.insert(
            TenantEntity(
                id = id,
                slug = "t-${id}",
                displayName = "Tenant",
                status = status,
                createdAt = now - (ageHours + 1).hours,
                updatedAt = now - ageHours.hours,
            ),
        )
        return id
    }

    @Test
    fun `purgeExpiredTenants deletes tenants pending longer than the retention window`() {
        val expired = seedTenant(TenantStatus.PENDING_DELETION, ageHours = 25)

        val purged = authService.purgeExpiredTenants(24.hours)

        assertEquals(1, purged)
        assertNull(tenantRepository.findById(expired))
    }

    @Test
    fun `purgeExpiredTenants keeps tenants still within the retention window`() {
        val fresh = seedTenant(TenantStatus.PENDING_DELETION, ageHours = 1)

        val purged = authService.purgeExpiredTenants(24.hours)

        assertEquals(0, purged)
        assertNotNull(tenantRepository.findById(fresh))
    }

    @Test
    fun `purgeExpiredTenants never touches ACTIVE or DISABLED tenants`() {
        val active = seedTenant(TenantStatus.ACTIVE, ageHours = 100)
        val disabled = seedTenant(TenantStatus.DISABLED, ageHours = 100)

        val purged = authService.purgeExpiredTenants(24.hours)

        assertEquals(0, purged)
        assertNotNull(tenantRepository.findById(active))
        assertNotNull(tenantRepository.findById(disabled))
    }

    @Test
    fun `reactivateTenant flips a pending-deletion tenant back to ACTIVE and audits it`() {
        val id = seedTenant(TenantStatus.PENDING_DELETION, ageHours = 1)

        val result = authService.reactivateTenant(id, UUID.randomUUID())

        assertEquals(TenantStatus.ACTIVE, result?.status)
        assertEquals(TenantStatus.ACTIVE, tenantRepository.findById(id)?.status)
        val audit = auditRepository.query(action = "TENANT_REACTIVATED", targetId = id)
        assertEquals(1, audit.size)
    }

    @Test
    fun `reactivateTenant returns null for an unknown tenant`() {
        assertNull(authService.reactivateTenant(UUID.randomUUID(), UUID.randomUUID()))
    }
}
