package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.infrastructure.security.SigningKey
import io.github.raginlundf.racingmanager.infrastructure.tables.SigningKeyTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import kotlin.time.Clock

/** Persists [SigningKey]s for [io.github.raginlundf.racingmanager.infrastructure.DeploymentMode.LOCAL]
    deployments. Only one row is `active` at a time — that is the key used to
    sign newly issued tokens; all rows remain available for verification by `kid`
    so tokens issued before a rotation keep validating until they expire. */
class SigningKeyRepository {

    fun findActive(): SigningKey.Rsa? = transaction {
        SigningKeyTable.selectAll().where { SigningKeyTable.active eq true }
            .singleOrNull()
            ?.toSigningKey()
    }

    fun findByKid(kid: String): SigningKey.Rsa? = transaction {
        SigningKeyTable.selectAll().where { SigningKeyTable.kid eq kid }
            .singleOrNull()
            ?.toSigningKey()
    }

    /** Inserts [key] as the new active signing key, deactivating any previously
        active key (which remains available for verification). */
    fun insertAsActive(key: SigningKey.Rsa) = transaction {
        SigningKeyTable.update({ SigningKeyTable.active eq true }) {
            it[active] = false
        }
        SigningKeyTable.insert {
            it[id] = UUID.randomUUID()
            it[kid] = key.kid
            it[algorithm] = key.algorithm
            it[publicKey] = Base64.getEncoder().encodeToString(key.publicKey.encoded)
            it[privateKey] = key.privateKey?.let { pk -> Base64.getEncoder().encodeToString(pk.encoded) }
            it[active] = true
            it[createdAt] = Clock.System.now()
        }
    }

    private fun ResultRow.toSigningKey(): SigningKey.Rsa {
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(this[SigningKeyTable.publicKey])),
        ) as RSAPublicKey
        val privateKey = this[SigningKeyTable.privateKey]?.let { encoded ->
            keyFactory.generatePrivate(
                PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)),
            ) as RSAPrivateKey
        }
        return SigningKey.Rsa(
            kid = this[SigningKeyTable.kid],
            algorithm = this[SigningKeyTable.algorithm],
            publicKey = publicKey,
            privateKey = privateKey,
            active = this[SigningKeyTable.active],
        )
    }
}
