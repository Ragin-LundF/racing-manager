package io.github.raginlundf.racingmanager.api.knockout.models

import kotlinx.serialization.Serializable

@Serializable
data class ManualPairingEntryModel(
    val participant1Id: String,
    val participant2Id: String? = null,
)
