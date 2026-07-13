package io.github.raginlundf.racingmanager.infrastructure.security

import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import java.util.UUID

class JwtServiceTest {

    private val keyProvider = LocalJwtKeyProvider(SigningKeyRepository())
    private val jwtService = JwtService(keyProvider)

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
        val token = jwtService.issueAccessToken(userId, tenantId, setOf("rm:admin", "rm:user"), ttl = 15.minutes)

        val principal = jwtService.verifyAccessToken(token)

        assertEquals(expected = userId, actual = principal?.userId)
        assertEquals(expected = tenantId, actual = principal?.tenantId)
        assertEquals(expected = setOf("rm:admin", "rm:user"), actual = principal?.scopes)
        assertNull(principal?.eventId)
    }

    @Test
    fun `spectator token carries the event id`() {
        val userId = UUID.randomUUID()
        val tenantId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val token = jwtService.issueAccessToken(userId, tenantId, setOf("rm:spectator"), eventId = eventId, ttl = 15.minutes)

        val principal = jwtService.verifyAccessToken(token)

        assertEquals(expected = eventId, actual = principal?.eventId)
    }

    @Test
    fun `expired token fails verification`() {
        val token = jwtService.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), setOf("rm:user"), ttl = (-1).seconds)

        assertNull(jwtService.verifyAccessToken(token))
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
        val rogueService = JwtService(object : JwtKeyProvider {
            override fun signingKey() = rogueKey
            override fun verificationKey(kid: String) = null
        })
        val token = rogueService.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), setOf("rm:user"), ttl = 15.minutes)

        assertNull(jwtService.verifyAccessToken(token))
    }

    @Test
    fun `issued token verifies back with a shared-secret key`() {
        val secretKey = SigningKey.Secret(kid = "hs-key", algorithm = "HS256", secret = "test-secret", active = true)
        val secretService = JwtService(object : JwtKeyProvider {
            override fun signingKey() = secretKey
            override fun verificationKey(kid: String) = if (kid == secretKey.kid) secretKey else null
        })
        val userId = UUID.randomUUID()
        val tenantId = UUID.randomUUID()

        val token = secretService.issueAccessToken(userId, tenantId, setOf("rm:user"), ttl = 15.minutes)
        val principal = secretService.verifyAccessToken(token)

        assertEquals(expected = userId, actual = principal?.userId)
        assertEquals(expected = tenantId, actual = principal?.tenantId)
    }

    @Test
    fun `token signed with a different secret fails verification`() {
        val signingKey = SigningKey.Secret(kid = "hs-key", algorithm = "HS256", secret = "secret-a", active = true)
        val signingService = JwtService(object : JwtKeyProvider {
            override fun signingKey() = signingKey
            override fun verificationKey(kid: String) = signingKey
        })
        val verifyingService = JwtService(object : JwtKeyProvider {
            override fun signingKey() = signingKey
            override fun verificationKey(kid: String) =
                SigningKey.Secret(kid = "hs-key", algorithm = "HS256", secret = "secret-b", active = true)
        })
        val token = signingService.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), setOf("rm:user"), ttl = 15.minutes)

        assertNull(verifyingService.verifyAccessToken(token))
    }

    @Test
    fun `tampered token fails verification`() {
        val token = jwtService.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), setOf("rm:user"), ttl = 15.minutes)
        // Flip a character in the middle of the signature, not the last character of the
        // token: trailing base64 characters can carry don't-care bits that some decoders
        // mask off, so two different trailing characters can decode to the same signature
        // bytes — a real base64 canonicalization edge case, not something to tolerate here.
        val mid = token.length / 2
        val tampered = token.substring(0, mid) + (if (token[mid] == 'A') 'B' else 'A') + token.substring(mid + 1)

        assertNull(jwtService.verifyAccessToken(tampered))
    }
}
