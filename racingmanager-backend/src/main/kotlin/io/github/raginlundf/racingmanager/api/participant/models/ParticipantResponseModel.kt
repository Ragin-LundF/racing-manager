package io.github.raginlundf.racingmanager.api.participant.models

import kotlinx.serialization.Serializable

@Serializable
data class ParticipantResponseModel(
    val id: String,
    val eventId: String,
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
    val status: String,
    val sortOrder: Int? = null,
    val vehicle: VehicleResponseModel? = null,
    val createdAt: String,
    val updatedAt: String? = null,
)

@Serializable
data class VehicleResponseModel(
    val id: String,
    val name: String,
    val category: String? = null,
)

@Serializable
data class CreateParticipantRequestModel(
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
    val vehicleName: String? = null,
    val vehicleCategory: String? = null,
)

@Serializable
data class UpdateParticipantRequestModel(
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
)

@Serializable
data class RandomizeRequestModel(
    val force: Boolean = false,
)

@Serializable
data class RandomizeResponseModel(
    val seed: Long,
    val alreadyRandomized: Boolean = false,
)

@Serializable
data class ImportCsvRequestModel(
    val rows: List<CsvRowModel>,
)

@Serializable
data class CsvRowModel(
    val startNumber: Int? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val club: String? = null,
    val vehicleName: String? = null,
    val vehicleCategory: String? = null,
)

@Serializable
data class ImportResponseModel(
    val created: Int,
    val errors: List<ImportErrorModel>,
)

@Serializable
data class ImportErrorModel(
    val rowIndex: Int,
    val message: String,
)
