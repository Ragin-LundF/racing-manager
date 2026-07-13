package io.github.raginlundf.racingmanager.application.sync

import io.github.raginlundf.racingmanager.domain.sync.PairedInstanceEntity

sealed interface PairResult {
    data class Success(val instance: PairedInstanceEntity) : PairResult
    data object InvalidOrExpiredCode : PairResult
}
