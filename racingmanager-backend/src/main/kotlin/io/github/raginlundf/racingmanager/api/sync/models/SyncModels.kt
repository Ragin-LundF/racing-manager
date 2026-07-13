package io.github.raginlundf.racingmanager.api.sync.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PairingTokenResponseModel(
    val pairingCode: String,
    val expiresIn: Long,
)

@Serializable
data class PairRequestModel(
    val pairingCode: String,
    val localInstanceId: String,
)

@Serializable
data class PairResponseModel(
    val localInstanceId: String,
    val tenantId: String,
)

@Serializable
data class PairedInstanceResponseModel(
    val id: String,
    val status: String,
    val pairedAt: String,
    val lastSyncAt: String? = null,
)

@Serializable
data class SyncResultsRequestModel(
    val eventId: String,
    /** The same JSON shape already produced by `GET .../results/backup` —
        carried through opaquely (design deviation notes on Slice I). */
    val results: JsonElement,
)

@Serializable
data class SyncResultsResponseModel(
    val syncedResultId: String,
    val eventId: String,
    val status: String,
)
