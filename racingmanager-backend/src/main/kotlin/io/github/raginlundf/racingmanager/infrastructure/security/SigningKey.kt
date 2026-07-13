package io.github.raginlundf.racingmanager.infrastructure.security

import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/** One JWT signing/verification key, identified by [kid]. Never log or
    serialize a [SigningKey] — it may carry private key or secret material. */
sealed interface SigningKey {
    val kid: String
    val algorithm: String
    val active: Boolean

    /** An asymmetric RS256 key pair. [privateKey] is null for a
        verification-only key (e.g. a hosted deployment may retire a key's
        private material while still accepting tokens it already issued). */
    data class Rsa(
        override val kid: String,
        override val algorithm: String,
        val publicKey: RSAPublicKey,
        val privateKey: RSAPrivateKey?,
        override val active: Boolean,
    ) : SigningKey {
        override fun toString(): String = "SigningKey.Rsa(kid=$kid, algorithm=$algorithm, active=$active)"
    }

    /** A symmetric HS256 shared secret — simpler to operate than an RSA key
        pair for a single-instance hosted deployment, at the cost of every
        verifier needing the same secret (there is no public/private split,
        so a "retired, verification-only" key isn't possible for HS256). */
    data class Secret(
        override val kid: String,
        override val algorithm: String,
        val secret: String,
        override val active: Boolean,
    ) : SigningKey {
        override fun toString(): String = "SigningKey.Secret(kid=$kid, algorithm=$algorithm, active=$active)"
    }
}
