package io.github.raginlundf.racingmanager.application.qualification

import io.github.raginlundf.racingmanager.domain.qualification.QualificationStatus

sealed interface ReopenResult {
    data object Success : ReopenResult
    data object QualificationNotFound : ReopenResult
    data class InvalidStatus(val current: QualificationStatus) : ReopenResult
}
