package io.github.raginlundf.racingmanager.api.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class SetupResponseModel(
    val userId: String,
    val username: String,
    val displayName: String,
)
