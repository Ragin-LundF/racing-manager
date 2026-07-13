package io.github.raginlundf.racingmanager.infrastructure.security

import io.ktor.server.config.ApplicationConfig
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Loads JWT signing keys from deployment configuration
    (`racingmanager.jwt.keys`, typically populated via environment variables
    at deploy time) — a [io.github.raginlundf.racingmanager.infrastructure.DeploymentMode.HOSTED]
    deployment manages its own keys and rotation through its deployment
    pipeline rather than generating them in-process. Configuration must never
    contain a committed secret.

    Each entry is either an RSA key pair (`publicKey`/`privateKey`, RS256) or
    a shared secret (`secret`, HS256) — a secret is simpler to operate for a
    single-instance deployment, at the cost of every verifier needing the
    same secret. */
class HostedJwtKeyProvider(private val keys: List<SigningKey>) : JwtKeyProvider {

    override fun signingKey(): SigningKey =
        keys.singleOrNull { it.active && it.canSign() }
            ?: error("Hosted JWT key configuration must declare exactly one active key with signing material")

    override fun verificationKey(kid: String): SigningKey? = keys.find { it.kid == kid }

    private fun SigningKey.canSign(): Boolean = when (this) {
        is SigningKey.Rsa -> privateKey != null
        is SigningKey.Secret -> true
    }

    companion object {
        private const val DEFAULT_RSA_ALGORITHM = "RS256"
        private const val DEFAULT_SECRET_ALGORITHM = "HS256"

        fun fromConfig(config: ApplicationConfig): HostedJwtKeyProvider {
            val keys = config.configList("racingmanager.jwt.keys").map { it.toSigningKey() }
            require(keys.isNotEmpty()) { "racingmanager.jwt.keys must declare at least one key in hosted mode" }
            return HostedJwtKeyProvider(keys)
        }

        private fun ApplicationConfig.toSigningKey(): SigningKey {
            val kid = property("kid").getString()
            val active = propertyOrNull("active")?.getString()?.toBoolean() ?: false
            val secret = propertyOrNull("secret")?.getString()
            if (secret != null) {
                val algorithm = propertyOrNull("algorithm")?.getString() ?: DEFAULT_SECRET_ALGORITHM
                return SigningKey.Secret(kid, algorithm, secret, active)
            }

            val keyFactory = KeyFactory.getInstance("RSA")
            val algorithm = propertyOrNull("algorithm")?.getString() ?: DEFAULT_RSA_ALGORITHM
            val publicKey = keyFactory.generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(property("publicKey").getString())),
            ) as RSAPublicKey
            val privateKey = propertyOrNull("privateKey")?.getString()?.let { encoded ->
                keyFactory.generatePrivate(
                    PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)),
                ) as RSAPrivateKey
            }
            return SigningKey.Rsa(kid, algorithm, publicKey, privateKey, active)
        }
    }
}
