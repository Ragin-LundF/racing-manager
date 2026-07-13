package io.github.raginlundf.racingmanager.api.bootstrap.models

import kotlinx.serialization.Serializable

@Serializable
data class PackagedParticipant(
    val id: String,
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
    val status: String,
    val sortOrder: Int? = null,
    val vehicleName: String? = null,
    val vehicleCategory: String? = null,
)

@Serializable
data class PackagedEvent(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String,
    val laneType: String,
    val measurementType: String,
    val maxParticipants: Int? = null,
    val participants: List<PackagedParticipant> = emptyList(),
)

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

/** The artifact an operator downloads and a local instance imports.
    Self-contained: [publicKey] travels with the artifact so a local instance
    (which has no relationship to the issuing tenant's signing keys) can
    verify [payload] was not corrupted or tampered with in transit. This
    proves **integrity**, not sender authenticity — a real trust-root /
    key-pinning scheme is out of scope for this release (see plan deviation
    notes). [payload] is the base64 of the canonical UTF-8 JSON encoding of a
    [LocalPackagePayload]; [signature] is the base64 RSA (SHA256withRSA)
    signature over those exact bytes. */
@Serializable
data class LocalPackageArtifact(
    val payload: String,
    val signature: String,
    val kid: String,
    val publicKey: String,
)

@Serializable
data class LocalPackageRequestModel(
    val eventIds: List<String>,
)

@Serializable
data class LocalPackageImportRequestModel(
    val artifact: LocalPackageArtifact,
    val dryRun: Boolean = false,
)

@Serializable
data class LocalPackageImportResponseModel(
    val localInstanceId: String,
    val tenantId: String,
    val importedEventIds: List<String>,
    val alreadyImported: Boolean,
    val dryRun: Boolean,
    val originTenantDisplayName: String,
)
