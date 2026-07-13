package io.github.raginlundf.racingmanager.api.participant.models

import kotlinx.serialization.Serializable

@Serializable
data class CsvRowModel(
    val startNumber: Int? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val club: String? = null,
    val vehicleName: String? = null,
    val vehicleCategory: String? = null,
)
