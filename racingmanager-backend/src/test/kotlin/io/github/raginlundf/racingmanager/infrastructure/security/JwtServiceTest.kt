package io.github.raginlundf.racingmanager.infrastructure.security

import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class JwtServiceTest {

    private val keyProvider = LocalJwtKeyProvider(repository = SigningKeyRepository())
    private val jwtService = JwtService(keyProvider = keyProvider)

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        keyProvider.ensureKeyExists()
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    @Test
    fun `issued token verifies back to the same principal`() {
        val userId = UUID.randomUUID()
        val tenantId = UUID.randomUUID()
        val token = jwtService.issueAccessToken(
            userId = userId,
            tenantId = tenantId,
            scopes = setOf("rm:admin", "rm:user"),
            ttl = 15.minutes
        )

        val principal = jwtService.verifyAccessToken(token = token)

        assertEquals(expected = userId, actual = principal?.userId)
        assertEquals(expected = tenantId, actual = principal?.tenantId)
        assertEquals(expected = setOf("rm:admin", "rm:user"), actual = principal?.scopes)
        assertNull(actual = principal?.eventId)
    }

    @Test
    fun `spectator token carries the event id`() {
        val userId = UUID.randomUUID()
        val tenantId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val token = jwtService.issueAccessToken(
            userId = userId,
            tenantId = tenantId,
            scopes = setOf("rm:spectator"),
            eventId = eventId,
            ttl = 15.minutes
        )

        val principal = jwtService.verifyAccessToken(token = token)

        assertEquals(expected = eventId, actual = principal?.eventId)
    }

    @Test
    fun `expired token fails verification`() {
        val token = jwtService.issueAccessToken(
            userId = UUID.randomUUID(),
            tenantId = UUID.randomUUID(),
            scopes = setOf("rm:user"),
            ttl = (-1).seconds
        )

        assertNull(actual = jwtService.verifyAccessToken(token = token))
    }

    @Test
    fun `token signed with an unknown kid fails verification`() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val rogueKey = SigningKey.Rsa(
            kid = "rogue-key",
            algorithm = "RS256",
            publicKey = keyPair.public as RSAPublicKey,
            privateKey = keyPair.private as RSAPrivateKey,
            active = true,
        )
        val rogueService = JwtService(keyProvider = object : JwtKeyProvider {
            override fun signingKey() = rogueKey
            override fun verificationKey(kid: String) = null
        })
        val token = rogueService.issueAccessToken(
            userId = UUID.randomUUID(),
            tenantId = UUID.randomUUID(),
            scopes = setOf("rm:user"),
            ttl = 15.minutes
        )

        assertNull(actual = jwtService.verifyAccessToken(token))
    }

    @Test
    fun `issued token verifies back with a shared-secret key`() {
        val secretKey = SigningKey.Secret(kid = "hs-key", algorithm = "HS256", secret = "test-secret", active = true)
        val secretService = JwtService(keyProvider = object : JwtKeyProvider {
            override fun signingKey() = secretKey
            override fun verificationKey(kid: String) = if (kid == secretKey.kid) secretKey else null
        })
        val userId = UUID.randomUUID()
        val tenantId = UUID.randomUUID()

        val token = secretService.issueAccessToken(
            userId = userId,
            tenantId = tenantId,
            scopes = setOf("rm:user"),
            ttl = 15.minutes
        )
        val principal = secretService.verifyAccessToken(token = token)

        assertEquals(expected = userId, actual = principal?.userId)
        assertEquals(expected = tenantId, actual = principal?.tenantId)
    }

    @Test
    fun `token signed with a different secret fails verification`() {
        val signingKey = SigningKey.Secret(kid = "hs-key", algorithm = "HS256", secret = "secret-a", active = true)
        val signingService = JwtService(keyProvider = object : JwtKeyProvider {
            override fun signingKey() = signingKey
            override fun verificationKey(kid: String) = signingKey
        })
        val verifyingService = JwtService(keyProvider = object : JwtKeyProvider {
            override fun signingKey() = signingKey
            override fun verificationKey(kid: String) = SigningKey.Secret(
                kid = "hs-key",
                algorithm = "HS256",
                secret = "secret-b",
                active = true
            )
        })
        val token = signingService.issueAccessToken(
            userId = UUID.randomUUID(),
            tenantId = UUID.randomUUID(),
            scopes = setOf("rm:user"),
            ttl = 15.minutes
        )

        assertNull(actual = verifyingService.verifyAccessToken(token = token))
    }

    @Test
    fun `tampered token fails verification`() {
        val token = jwtService.issueAccessToken(
            userId = UUID.randomUUID(),
            tenantId = UUID.randomUUID(),
            scopes = setOf("rm:user"),
            ttl = 15.minutes
        )
        // Flip a character in the middle of the signature, not the last character of the
        // token: trailing base64 characters can carry don't-care bits that some decoders
        // mask off, so two different trailing characters can decode to the same signature
        // bytes — a real base64 canonicalization edge case, not something to tolerate here.
        val mid = token.length / 2
        val tampered = token.substring(0, mid) + (
                if (token[mid] == 'A') 'B' else 'A'
                ) + token.substring(startIndex = mid + 1)

        assertNull(actual = jwtService.verifyAccessToken(token = tampered))
    }
}
