package io.github.raginlundf.racingmanager.api.knockout.models

import kotlinx.serialization.Serializable

@Serializable
data class KnockoutTournamentResponseModel(
    val id: String,
    val eventId: String,
    val status: String,
    val pairingMode: String,
    val qualificationId: String,
    val createdAt: String,
    val updatedAt: String? = null,
    val finalizedAt: String? = null,
    val finalizedBy: String? = null,
)
