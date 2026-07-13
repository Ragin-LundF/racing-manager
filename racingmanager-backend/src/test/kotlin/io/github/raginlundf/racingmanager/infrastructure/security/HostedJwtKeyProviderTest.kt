package io.github.raginlundf.racingmanager.infrastructure.security

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.HoconApplicationConfig
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class HostedJwtKeyProviderTest {

    private fun generateKeyPairBase64(): Pair<String, String> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val privateKey = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        return publicKey to privateKey
    }

    @Test
    fun `signingKey returns the single active key with private material`() {
        val (publicKey, privateKey) = generateKeyPairBase64()
        val config = HoconApplicationConfig(
            ConfigFactory.parseString(
                """
                racingmanager.jwt.keys = [
                    { kid = "k1", publicKey = "$publicKey", privateKey = "$privateKey", active = true }
                ]
                """.trimIndent(),
            ),
        )

        val provider = HostedJwtKeyProvider.fromConfig(config)

        assertEquals(expected = "k1", actual = provider.signingKey().kid)
    }

    @Test
    fun `verificationKey finds a retired public-only key by kid`() {
        val (activePublic, activePrivate) = generateKeyPairBase64()
        val (retiredPublic, _) = generateKeyPairBase64()
        val config = HoconApplicationConfig(
            ConfigFactory.parseString(
                """
                racingmanager.jwt.keys = [
                    { kid = "k1", publicKey = "$activePublic", privateKey = "$activePrivate", active = true },
                    { kid = "k0", publicKey = "$retiredPublic", active = false }
                ]
                """.trimIndent(),
            ),
        )

        val provider = HostedJwtKeyProvider.fromConfig(config)

        assertEquals(expected = "k1", actual = provider.signingKey().kid)
        val retired = assertIs<SigningKey.Rsa>(provider.verificationKey("k0"))
        assertEquals(expected = "k0", actual = retired.kid)
        assertNull(retired.privateKey)
        assertNull(provider.verificationKey("unknown"))
    }

    @Test
    fun `signingKey fails when no active key has private material`() {
        val (publicKey, _) = generateKeyPairBase64()
        val config = HoconApplicationConfig(
            ConfigFactory.parseString(
                """
                racingmanager.jwt.keys = [
                    { kid = "k0", publicKey = "$publicKey", active = false }
                ]
                """.trimIndent(),
            ),
        )

        val provider = HostedJwtKeyProvider.fromConfig(config)

        assertFailsWith<IllegalStateException> { provider.signingKey() }
    }

    @Test
    fun `fromConfig rejects an empty key list`() {
        val config = HoconApplicationConfig(
            ConfigFactory.parseString("racingmanager.jwt.keys = []"),
        )

        assertFailsWith<IllegalArgumentException> { HostedJwtKeyProvider.fromConfig(config) }
    }

    @Test
    fun `fromConfig loads an HS256 shared secret key`() {
        val config = HoconApplicationConfig(
            ConfigFactory.parseString(
                """
                racingmanager.jwt.keys = [
                    { kid = "s1", algorithm = "HS256", secret = "super-secret-value", active = true }
                ]
                """.trimIndent(),
            ),
        )

        val provider = HostedJwtKeyProvider.fromConfig(config)

        val signingKey = provider.signingKey()
        val secretKey = assertIs<SigningKey.Secret>(signingKey)
        assertEquals(expected = "s1", actual = secretKey.kid)
        assertEquals(expected = "super-secret-value", actual = secretKey.secret)
    }

    @Test
    fun `fromConfig defaults a secret key's algorithm to HS256`() {
        val config = HoconApplicationConfig(
            ConfigFactory.parseString(
                """
                racingmanager.jwt.keys = [
                    { kid = "s1", secret = "super-secret-value", active = true }
                ]
                """.trimIndent(),
            ),
        )

        val secretKey = assertIs<SigningKey.Secret>(HostedJwtKeyProvider.fromConfig(config).signingKey())

        assertEquals(expected = "HS256", actual = secretKey.algorithm)
    }

    @Test
    fun `secret key can mix with rsa keys in the same list`() {
        val (publicKey, privateKey) = generateKeyPairBase64()
        val config = HoconApplicationConfig(
            ConfigFactory.parseString(
                """
                racingmanager.jwt.keys = [
                    { kid = "s0", secret = "retired-secret", active = false },
                    { kid = "k1", publicKey = "$publicKey", privateKey = "$privateKey", active = true }
                ]
                """.trimIndent(),
            ),
        )

        val provider = HostedJwtKeyProvider.fromConfig(config)

        assertEquals(expected = "k1", actual = provider.signingKey().kid)
        assertIs<SigningKey.Secret>(provider.verificationKey("s0"))
        assertIs<SigningKey.Rsa>(provider.verificationKey("k1"))
    }
}
