package io.github.raginlundf.racingmanager.domain.sync

import kotlin.time.Instant
import java.util.UUID

/** A short-lived, single-use code a tenant admin issues so a local instance
    can pair itself without ever handling a copied admin password
    (design §I.1). */
data class PairingCodeEntity(
    val id: UUID,
    val tenantId: UUID,
    val expiresAt: Instant,
    val consumed: Boolean = false,
)
