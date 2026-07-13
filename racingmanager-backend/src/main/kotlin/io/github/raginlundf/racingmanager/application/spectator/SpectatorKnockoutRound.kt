package io.github.raginlundf.racingmanager.application.spectator

data class SpectatorKnockoutRound(
    val roundNumber: Int,
    val matches: List<SpectatorKnockoutMatch>,
)
