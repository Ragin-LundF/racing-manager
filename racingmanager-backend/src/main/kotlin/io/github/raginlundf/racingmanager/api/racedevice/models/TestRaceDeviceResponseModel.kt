package io.github.raginlundf.racingmanager.api.racedevice.models

import kotlinx.serialization.Serializable

@Serializable
data class TestRaceDeviceResponseModel(
    val ok: Boolean,
    val pingMs: Long? = null,
    val error: String? = null,
)
