package io.github.raginlundf.racingmanager.domain.knockout

import java.util.UUID

data class KnockoutRankedParticipant(
    val participantId: UUID,
    val startNumber: Int,
    val firstName: String,
    val lastName: String,
    val club: String? = null,
    val qualificationRank: Int,
)
