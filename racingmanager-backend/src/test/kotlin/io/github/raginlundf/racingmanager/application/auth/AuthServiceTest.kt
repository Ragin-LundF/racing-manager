package io.github.raginlundf.racingmanager.application.auth

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
}
