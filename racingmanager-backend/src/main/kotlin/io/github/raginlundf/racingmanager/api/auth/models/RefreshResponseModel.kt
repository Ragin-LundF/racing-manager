package io.github.raginlundf.racingmanager.api.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class RefreshResponseModel(
    val accessToken: String,
    val expiresIn: Long,
)
