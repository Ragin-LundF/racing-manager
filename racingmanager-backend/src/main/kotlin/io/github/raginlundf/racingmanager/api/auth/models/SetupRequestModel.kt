package io.github.raginlundf.racingmanager.api.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class SetupRequestModel(
    val username: String,
    val password: String,
    val displayName: String,
)
