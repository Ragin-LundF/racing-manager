package io.github.raginlundf.racingmanager.api.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class LogoutRequestModel(
    val refreshToken: String? = null,
)
