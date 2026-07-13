package io.github.raginlundf.racingmanager.api.knockout.models

import kotlinx.serialization.Serializable

@Serializable
data class SetManualPairingsRequestModel(
    val pairings: List<ManualPairingEntryModel>,
)
