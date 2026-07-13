package io.github.raginlundf.racingmanager.application.spectator

data class SpectatorKnockoutState(
    val status: String,
    val pairingMode: String,
    val rounds: List<SpectatorKnockoutRound>,
)
