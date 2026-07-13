package io.github.raginlundf.racingmanager.application.qualification

import io.github.raginlundf.racingmanager.domain.qualification.QualificationStatus

sealed interface FinalizeResult {
    data object Success : FinalizeResult
    data object QualificationNotFound : FinalizeResult
    data class InvalidStatus(val current: QualificationStatus) : FinalizeResult
    data class IncompleteHeats(val count: Int) : FinalizeResult
}
