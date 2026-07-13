package io.github.raginlundf.racingmanager.application.participant

import io.github.raginlundf.racingmanager.domain.participant.EventSeedEntity

sealed interface RandomizeResult {
    data class Success(val seed: Long) : RandomizeResult
    data object EventNotFound : RandomizeResult
    data object EventNotActive : RandomizeResult
    data class AlreadyRandomized(val seed: EventSeedEntity) : RandomizeResult
}
