package io.github.raginlundf.racingmanager.api.bootstrap.models

import kotlinx.serialization.Serializable

@Serializable
data class LocalPackageRequestModel(
    val eventIds: List<String>,
)
