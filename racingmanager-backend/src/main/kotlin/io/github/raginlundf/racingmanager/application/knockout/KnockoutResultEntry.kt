package io.github.raginlundf.racingmanager.application.knockout

import java.util.UUID

data class KnockoutResultEntry(
    val rank: Int,
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    val startNumber: Int,
    val club: String? = null,
)
