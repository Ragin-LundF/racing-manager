package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

/** Local-side ledger of every bootstrap package already imported into this
    instance (design §H.3) — the sole idempotency/replay guard, since there is
    no live channel back to the issuing hosted tenant to check revocation. */
object ImportedPackageTable : Table("imported_packages") {
    val packageId = javaUUID("package_id")
    val originTenantId = javaUUID("origin_tenant_id")
    val importedEventIds = text("imported_event_ids")
    val importedAt = timestamp("imported_at")

    override val primaryKey = PrimaryKey(packageId)
}
