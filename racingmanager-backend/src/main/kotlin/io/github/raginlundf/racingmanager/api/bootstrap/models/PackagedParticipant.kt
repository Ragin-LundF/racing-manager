package io.github.raginlundf.racingmanager.api.bootstrap.models

import kotlinx.serialization.Serializable

@Serializable
data class PackagedParticipant(
    val id: String,
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
    val status: String,
    val sortOrder: Int? = null,
    val vehicleName: String? = null,
    val vehicleCategory: String? = null,
)
