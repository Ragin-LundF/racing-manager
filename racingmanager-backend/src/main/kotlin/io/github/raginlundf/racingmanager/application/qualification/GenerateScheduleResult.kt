package io.github.raginlundf.racingmanager.application.qualification

import io.github.raginlundf.racingmanager.domain.qualification.QualificationEntity
import io.github.raginlundf.racingmanager.domain.qualification.QualificationStatus

sealed interface GenerateScheduleResult {
    data class Success(val qualification: QualificationEntity) : GenerateScheduleResult
    data object QualificationNotFound : GenerateScheduleResult
    data class InvalidStatus(val current: QualificationStatus) : GenerateScheduleResult
    data object NotEnoughParticipants : GenerateScheduleResult
    data object HeatsAlreadyExist : GenerateScheduleResult
}
