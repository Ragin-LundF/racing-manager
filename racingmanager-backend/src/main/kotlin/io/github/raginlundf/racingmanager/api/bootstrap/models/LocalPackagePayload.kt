package io.github.raginlundf.racingmanager.api.bootstrap.models

import kotlinx.serialization.Serializable

/** The signed content of a bootstrap package (design §H) — events are
    exported with their participants only. A club prepares an event online
    (name, settings, entry list) before race day; heats/results don't exist
    yet at packaging time, so there is nothing else to carry offline. */
@Serializable
data class LocalPackagePayload(
    val packageId: String,
    val schemaVersion: Int = 1,
    val tenantId: String,
    val tenantSlug: String? = null,
    val tenantDisplayName: String,
    val createdAt: String,
    val expiresAt: String,
    val events: List<PackagedEvent>,
)
