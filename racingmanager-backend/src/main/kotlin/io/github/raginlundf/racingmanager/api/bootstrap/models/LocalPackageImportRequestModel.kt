package io.github.raginlundf.racingmanager.api.bootstrap.models

import kotlinx.serialization.Serializable

@Serializable
data class LocalPackageImportRequestModel(
    val artifact: LocalPackageArtifact,
    val dryRun: Boolean = false,
)
