package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.auth.RefreshTokenEntity
import io.github.raginlundf.racingmanager.domain.tenant.TenantEntity
import io.github.raginlundf.racingmanager.domain.user.UserEntity
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import java.util.UUID

class RefreshTokenRepositoryTest {

    private val repository = RefreshTokenRepository()
    private val userRepository = UserRepository()
    private val tenantRepository = TenantRepository()

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    private fun createUserAndTenant(): Pair<UserEntity, TenantEntity> {
        val tenant = TenantEntity(id = UUID.randomUUID(), displayName = "T", createdAt = Clock.System.now())
        tenantRepository.insert(tenant)
        val user = UserEntity(
            id = UUID.randomUUID(),
            tenantId = tenant.id,
            username = "user",
            passwordHash = "hash",
            displayName = "User",
            role = UserRole.ADMIN,
            createdAt = Clock.System.now(),
        )
        userRepository.insert(user)
        return user to tenant
    }

    @Test
    fun `insert and findById round-trips a refresh token`() {
        val (user, tenant) = createUserAndTenant()
        val now = Clock.System.now()
        val token = RefreshTokenEntity(
            id = UUID.randomUUID(),
            userId = user.id,
            tenantId = tenant.id,
            tokenVersion = 0,
            createdAt = now,
            expiresAt = now.plus(30.days),
        )

        repository.insert(token)

        val found = repository.findById(token.id)
        assertNotNull(found)
        assertEquals(expected = user.id, actual = found.userId)
        assertFalse(found.revoked)
    }

    @Test
    fun `revoke marks the token as revoked`() {
        val (user, tenant) = createUserAndTenant()
        val now = Clock.System.now()
        val token = RefreshTokenEntity(
            id = UUID.randomUUID(),
            userId = user.id,
            tenantId = tenant.id,
            tokenVersion = 0,
            createdAt = now,
            expiresAt = now.plus(30.days),
        )
        repository.insert(token)

        repository.revoke(token.id)

        assertTrue(repository.findById(token.id)!!.revoked)
    }

    @Test
    fun `revokeAllForUser revokes every token for that user`() {
        val (user, tenant) = createUserAndTenant()
        val now = Clock.System.now()
        val tokenA = RefreshTokenEntity(UUID.randomUUID(), user.id, tenant.id, 0, now, now.plus(30.days))
        val tokenB = RefreshTokenEntity(UUID.randomUUID(), user.id, tenant.id, 0, now, now.plus(30.days))
        repository.insert(tokenA)
        repository.insert(tokenB)

        repository.revokeAllForUser(user.id)

        assertTrue(repository.findById(tokenA.id)!!.revoked)
        assertTrue(repository.findById(tokenB.id)!!.revoked)
    }

    @Test
    fun `incrementTokenVersion bumps the user's version`() {
        val (user, _) = createUserAndTenant()

        userRepository.incrementTokenVersion(user.id)

        assertEquals(expected = 1, actual = userRepository.findById(user.id)!!.tokenVersion)
    }

    @Test
    fun `isValid rejects a token issued under an older token version`() {
        val (user, tenant) = createUserAndTenant()
        val now = Clock.System.now()
        val token = RefreshTokenEntity(UUID.randomUUID(), user.id, tenant.id, tokenVersion = 0, now, now.plus(30.days))

        assertTrue(token.isValid(now, currentTokenVersion = 0))
        assertFalse(token.isValid(now, currentTokenVersion = 1))
    }
}
