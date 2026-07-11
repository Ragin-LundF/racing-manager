package io.github.raginlundf.racingmanager.domain.event

data class EventSettings(
    val laneType: LaneType = LaneType.TWO_LANE,
    val measurementType: MeasurementType = MeasurementType.SIMULATED,
    val maxParticipants: Int? = null,
)
