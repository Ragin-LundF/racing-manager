package io.github.raginlundf.racingmanager.api.qualification.models

import kotlinx.serialization.Serializable

@Serializable
data class SetupQualificationRequestModel(
    val numberOfRuns: Int = 2,
)
