package io.github.raginlundf.racingmanager.application.sync

import java.util.UUID

sealed interface PairingTokenResult {
    data class Success(val code: UUID, val expiresIn: Long) : PairingTokenResult
}
