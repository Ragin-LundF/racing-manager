package io.github.raginlundf.racingmanager.infrastructure.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import io.github.raginlundf.racingmanager.application.auth.RequestPrincipal
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import java.util.UUID

private const val CLAIM_TENANT_ID = "tenant_id"
private const val CLAIM_SCOPE = "scope"
private const val CLAIM_EVENT_ID = "event_id"

/** Issues and verifies JWT access tokens signed with the deployment's
    [JwtKeyProvider]. Access tokens are stateless — verification never touches
    the database; only the signing key lookup by `kid` does. Never logs a raw
    token or its claims. */
class JwtService(
    private val keyProvider: JwtKeyProvider,
    private val issuer: String? = null,
    private val audience: String? = null,
) {
    private val clock: Clock = Clock.System

    fun issueAccessToken(
        userId: UUID,
        tenantId: UUID,
        scopes: Set<String>,
        eventId: UUID? = null,
        ttl: Duration,
    ): String {
        val key = keyProvider.signingKey()
        val algorithm = signingAlgorithm(key)
        val now = clock.now()

        val builder = JWT.create()
            .withSubject(userId.toString())
            .withClaim(CLAIM_TENANT_ID, tenantId.toString())
            .withClaim(CLAIM_SCOPE, scopes.joinToString(" "))
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(now.toJavaInstant())
            .withExpiresAt(now.plus(ttl).toJavaInstant())
            .withKeyId(key.kid)
        eventId?.let { builder.withClaim(CLAIM_EVENT_ID, it.toString()) }
        issuer?.let { builder.withIssuer(it) }
        audience?.let { builder.withAudience(it) }
        return builder.sign(algorithm)
    }

    /** Returns null for any invalid, expired, tampered, or unknown-key token —
        callers treat that uniformly as "unauthenticated". */
    fun verifyAccessToken(token: String): RequestPrincipal? {
        val kid = runCatching { JWT.decode(token).keyId }.getOrNull() ?: return null
        val key = keyProvider.verificationKey(kid) ?: return null
        val algorithm = verificationAlgorithm(key)

        val verification = JWT.require(algorithm)
        issuer?.let { verification.withIssuer(it) }
        audience?.let { verification.withAudience(it) }

        val decoded = try {
            verification.build().verify(token)
        } catch (_: JWTVerificationException) {
            return null
        }

        val tenantId = decoded.getClaim(CLAIM_TENANT_ID).asString() ?: return null
        val scopeClaim = decoded.getClaim(CLAIM_SCOPE).asString() ?: return null
        val eventIdClaim = decoded.getClaim(CLAIM_EVENT_ID).asString()

        return RequestPrincipal(
            userId = UUID.fromString(decoded.subject),
            tenantId = UUID.fromString(tenantId),
            scopes = scopeClaim.split(" ").filter { it.isNotBlank() }.toSet(),
            eventId = eventIdClaim?.let { UUID.fromString(it) },
            jti = decoded.id,
            expiresAt = decoded.expiresAtAsInstant.toKotlinInstant(),
        )
    }

    private fun signingAlgorithm(key: SigningKey): Algorithm = when (key) {
        is SigningKey.Rsa -> {
            val privateKey = requireNotNull(key.privateKey) { "Signing key '${key.kid}' has no private key material" }
            Algorithm.RSA256(key.publicKey, privateKey)
        }
        is SigningKey.Secret -> Algorithm.HMAC256(key.secret)
    }

    private fun verificationAlgorithm(key: SigningKey): Algorithm = when (key) {
        is SigningKey.Rsa -> Algorithm.RSA256(key.publicKey, null)
        is SigningKey.Secret -> Algorithm.HMAC256(key.secret)
    }
}
