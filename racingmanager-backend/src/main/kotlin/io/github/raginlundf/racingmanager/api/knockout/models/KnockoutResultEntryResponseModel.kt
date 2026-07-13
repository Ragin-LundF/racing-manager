package io.github.raginlundf.racingmanager.api.knockout.models

import kotlinx.serialization.Serializable

@Serializable
data class KnockoutResultEntryResponseModel(
    val rank: Int,
    val participantId: String,
    val firstName: String,
    val lastName: String,
    val startNumber: Int,
    val club: String? = null,
)
