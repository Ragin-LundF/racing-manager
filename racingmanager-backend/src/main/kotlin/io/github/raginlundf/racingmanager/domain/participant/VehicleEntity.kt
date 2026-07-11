package io.github.raginlundf.racingmanager.domain.participant

import java.util.UUID

data class VehicleEntity(
    val id: UUID,
    val participantId: UUID,
    val name: String,
    val category: String? = null,
)
