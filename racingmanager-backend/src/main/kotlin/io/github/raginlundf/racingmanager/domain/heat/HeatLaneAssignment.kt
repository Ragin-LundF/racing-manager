package io.github.raginlundf.racingmanager.domain.heat

import java.util.UUID

data class HeatLaneAssignment(
    val lane: Int,
    val participantId: UUID,
    val participantStartNumber: Int,
    val participantFirstName: String,
    val participantLastName: String,
)
