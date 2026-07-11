package io.github.raginlundf.racingmanager.application.auth

import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SessionRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.UUID

class AuthServiceTest {

    private val userRepository = UserRepository()
    private val sessionRepository = SessionRepository()
    private val auditRepository = AuditRepository()
    private val passwordHasher = PasswordHasher()
    private val authService = AuthService(userRepository, sessionRepository, auditRepository, passwordHasher)

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
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
    fun `setupAdmin creates admin user and returns Success`() {
        val result = authService.setupAdmin("admin", "password123", "Admin User")

        val success = assertIs<SetupResult.Success>(result)
        assertEquals("admin", success.user.username)
        assertEquals("Admin User", success.user.displayName)
        assertEquals(UserRole.ADMIN, success.user.role)
        assertNotNull(success.user.id)
    }

    @Test
    fun `setupAdmin returns AlreadySetup on second call`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val result = authService.setupAdmin("admin2", "otherpass", "Other Admin")

        assertIs<SetupResult.AlreadySetup>(result)
    }

    @Test
    fun `login with valid credentials returns Success`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val result = authService.login("admin", "password123")

        val success = assertIs<LoginResult.Success>(result)
        assertEquals("admin", success.user.username)
        assertNotNull(success.session.id)
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
    fun `getSession returns Valid for active session`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val loginResult = authService.login("admin", "password123") as LoginResult.Success

        val result = authService.getSession(loginResult.session.id)

        val valid = assertIs<SessionResult.Valid>(result)
        assertEquals("admin", valid.user.username)
    }

    @Test
    fun `getSession returns NotFound for unknown session`() {
        val result = authService.getSession(UUID.randomUUID())

        assertIs<SessionResult.NotFound>(result)
    }

    @Test
    fun `logout deletes session`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val loginResult = authService.login("admin", "password123") as LoginResult.Success

        authService.logout(loginResult.session.id)

        val sessionResult = authService.getSession(loginResult.session.id)
        assertIs<SessionResult.NotFound>(sessionResult)
    }

    @Test
    fun `changePassword with valid credentials succeeds`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val loginResult = authService.login("admin", "password123") as LoginResult.Success

        val result = authService.changePassword(loginResult.user.id, "password123", "newpassword")

        assertIs<ChangePasswordResult.Success>(result)
    }

    @Test
    fun `changePassword with wrong current password fails`() {
        authService.setupAdmin("admin", "password123", "Admin User")
        val loginResult = authService.login("admin", "password123") as LoginResult.Success

        val result = authService.changePassword(loginResult.user.id, "wrongpassword", "newpassword")

        assertIs<ChangePasswordResult.InvalidCurrentPassword>(result)
    }

    @Test
    fun `changePassword for unknown user returns UserNotFound`() {
        val result = authService.changePassword(UUID.randomUUID(), "password123", "newpassword")

        assertIs<ChangePasswordResult.UserNotFound>(result)
    }
}
