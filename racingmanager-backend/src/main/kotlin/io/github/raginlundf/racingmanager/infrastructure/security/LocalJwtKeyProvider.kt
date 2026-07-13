package io.github.raginlundf.racingmanager.infrastructure.security

import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID

private const val RSA_KEY_SIZE = 2048
private const val ALGORITHM = "RS256"

/** Generates and persists its own RSA signing key on first use — a
    [io.github.raginlundf.racingmanager.infrastructure.DeploymentMode.LOCAL]
    deployment must be able to sign and verify JWTs without any external
    identity service or key management infrastructure. */
class LocalJwtKeyProvider(private val repository: SigningKeyRepository) : JwtKeyProvider {

    /** Ensures an active signing key exists, generating one on first run. Safe
        to call on every startup. */
    fun ensureKeyExists(): SigningKey.Rsa = repository.findActive() ?: generateAndPersist()

    override fun signingKey(): SigningKey =
        repository.findActive() ?: error("No active JWT signing key — call ensureKeyExists() during startup")

    override fun verificationKey(kid: String): SigningKey? = repository.findByKid(kid)

    /** Generates a new key and makes it the active signing key; the previous
        key remains stored for verification of tokens it already issued. */
    fun rotate(): SigningKey.Rsa = generateAndPersist()

    private fun generateAndPersist(): SigningKey.Rsa {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA").apply { initialize(RSA_KEY_SIZE) }
        val keyPair = keyPairGenerator.generateKeyPair()
        val key = SigningKey.Rsa(
            kid = UUID.randomUUID().toString(),
            algorithm = ALGORITHM,
            publicKey = keyPair.public as RSAPublicKey,
            privateKey = keyPair.private as RSAPrivateKey,
            active = true,
        )
        repository.insertAsActive(key)
        return key
    }
}
