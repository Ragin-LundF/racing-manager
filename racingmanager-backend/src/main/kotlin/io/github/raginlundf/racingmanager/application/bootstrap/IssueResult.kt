package io.github.raginlundf.racingmanager.application.bootstrap

import io.github.raginlundf.racingmanager.api.bootstrap.models.LocalPackageArtifact

sealed interface IssueResult {
    data class Success(val artifact: LocalPackageArtifact) : IssueResult
    data object EventNotFound : IssueResult
}
