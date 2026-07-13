package io.github.raginlundf.racingmanager.application.qualification

import io.github.raginlundf.racingmanager.domain.qualification.QualificationEntity

sealed interface SetupQualificationResult {
    data class Success(val qualification: QualificationEntity) : SetupQualificationResult
    data object EventNotFound : SetupQualificationResult
    data object EventNotActive : SetupQualificationResult
    data class AlreadyExists(val qualification: QualificationEntity) : SetupQualificationResult
    data object NotEnoughParticipants : SetupQualificationResult
}
