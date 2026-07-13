package io.github.raginlundf.racingmanager.application.auth

sealed interface RefreshResult {
    data class Success(val accessToken: String, val expiresInSeconds: Long) : RefreshResult
    data object Invalid : RefreshResult
}
