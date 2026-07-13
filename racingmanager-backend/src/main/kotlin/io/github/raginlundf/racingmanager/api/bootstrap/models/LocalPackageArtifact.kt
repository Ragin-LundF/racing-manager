package io.github.raginlundf.racingmanager.api.bootstrap.models

import kotlinx.serialization.Serializable

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
