package io.github.raginlundf.racingmanager.api.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class RefreshRequestModel(
    val refreshToken: String,
)
