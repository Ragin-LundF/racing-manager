package io.github.raginlundf.racingmanager.domain.spectator

import kotlin.time.Instant
import java.util.UUID

/** A single-use, short-lived exchange code (design §7/§F): the operator UI
    receives [id] (the code) after issuing a spectator token, never the raw
    [token] itself — the code is what may safely sit in a URL fragment or be
    read aloud, while the JWT it unlocks never does. */
data class SpectatorExchangeCodeEntity(
    val id: UUID,
    val tenantId: UUID,
    val eventId: UUID,
    val token: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val consumed: Boolean = false,
)
