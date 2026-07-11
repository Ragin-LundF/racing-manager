package io.github.raginlundf.racingmanager.api.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestModel(
    val username: String,
    val password: String,
)
