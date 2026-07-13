package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

/** Persisted JWT signing keys for [DeploymentMode.LOCAL] deployments. Public
    and private key material is stored Base64-encoded (X.509 / PKCS8); never
    select these columns into a log statement. */
object SigningKeyTable : Table("signing_keys") {
    val id = javaUUID("id")
    val kid = varchar("kid", 64).uniqueIndex()
    val algorithm = varchar("algorithm", 20)
    val publicKey = text("public_key")
    val privateKey = text("private_key").nullable()
    val active = bool("active")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
