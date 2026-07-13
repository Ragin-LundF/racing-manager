package io.github.raginlundf.racingmanager.application.heat

import io.github.raginlundf.racingmanager.domain.audit.AuditEntryEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.event.MeasurementType
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatLaneAssignment
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.domain.heat.LaneOutcome
import io.github.raginlundf.racingmanager.domain.heat.Measurement
import io.github.raginlundf.racingmanager.domain.participant.ParticipantStatus
import io.github.raginlundf.racingmanager.infrastructure.gateway.SimulationMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import java.util.UUID

class HeatService(
    private val heatRepository: HeatRepository,
    private val eventRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val auditRepository: AuditRepository,
    private val measurementGateway: MeasurementGateway = SimulationMeasurementGateway(),
) {
    private val clock: Clock = Clock.System
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _events = MutableSharedFlow<HeatServiceEvent>(extraBufferCapacity = 64)

    val events = _events.asSharedFlow()

    init {
        scope.launch {
            measurementGateway.events().collect { event ->
                handleGatewayEvent(event)
            }
        }
    }

    fun findByEventId(eventId: UUID): List<HeatEntity> {
        return heatRepository.findByEventId(eventId)
    }

    fun findById(id: UUID): HeatEntity? {
        return heatRepository.findById(id)
    }

    fun findLatestByEventId(eventId: UUID): HeatEntity? {
        return heatRepository.findLatestByEventId(eventId)
    }

    fun create(eventId: UUID, participantIds: List<UUID>, actorId: UUID): CreateHeatResult {
        val event = eventRepository.findById(eventId)
            ?: return CreateHeatResult.EventNotFound

        if (event.status != EventStatus.ACTIVE) {
            return CreateHeatResult.EventNotActive
        }

        val participants = participantIds.mapNotNull { participantRepository.findById(it) }
        if (participants.size != participantIds.size) {
            return CreateHeatResult.ParticipantNotFound
        }

        if (participants.any { it.status != ParticipantStatus.ACTIVE }) {
            return CreateHeatResult.ParticipantNotActive
        }

        val existingHeats = heatRepository.findByEventId(eventId)
        val round = 1
        val heatNumber = existingHeats.size + 1

        val now = clock.now()
        val heat = HeatEntity(
            id = UUID.randomUUID(),
            eventId = eventId,
            round = round,
            heatNumber = heatNumber,
            status = HeatStatus.PLANNED,
            lanes = participants.mapIndexed { index, p ->
                HeatLaneAssignment(
                    lane = index + 1,
                    participantId = p.id,
                    participantStartNumber = p.startNumber,
                    participantFirstName = p.firstName,
                    participantLastName = p.lastName,
                )
            },
            measurements = emptyList(),
            createdAt = now,
        )

        heatRepository.insert(heat)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_CREATED",
                targetType = "Heat",
                targetId = heat.id,
                summary = "Heat #$heatNumber created with ${participants.size} participants",
                occurredAt = now,
            ),
        )

        _events.tryEmit(HeatServiceEvent.HeatCreated(heat))
        return CreateHeatResult.Success(heat)
    }

    suspend fun arm(id: UUID, actorId: UUID): ArmHeatResult {
        val heat = heatRepository.findById(id)
            ?: return ArmHeatResult.NotFound

        if (heat.status != HeatStatus.PLANNED) {
            return ArmHeatResult.InvalidStatus(heat.status)
        }

        val now = clock.now()
        heatRepository.updateStatus(id, HeatStatus.ARMED, armedAt = now)

        val result = measurementGateway.arm(heat)
        if (result is GatewayArmResult.Error) {
            heatRepository.updateStatus(id, HeatStatus.PLANNED)
            return ArmHeatResult.GatewayError(result.message)
        }

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_ARMED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat armed",
                occurredAt = clock.now(),
            ),
        )

        val updated = heatRepository.findById(id)!!
        _events.tryEmit(HeatServiceEvent.HeatStateChanged(updated))
        return ArmHeatResult.Success(updated)
    }

    suspend fun start(id: UUID, actorId: UUID): StartHeatResult {
        val heat = heatRepository.findById(id)
            ?: return StartHeatResult.NotFound

        if (heat.status != HeatStatus.ARMED) {
            return StartHeatResult.InvalidStatus(heat.status)
        }

        val now = clock.now()
        heatRepository.updateStatus(id, HeatStatus.STARTED, startedAt = now)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_STARTED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat started",
                occurredAt = clock.now(),
            ),
        )

        val updated = heatRepository.findById(id)!!
        val event = eventRepository.findById(updated.eventId)
        if (event?.settings?.measurementType == MeasurementType.SIMULATED) {
            measurementGateway.simulateHeat(updated)
        }

        _events.tryEmit(HeatServiceEvent.HeatStateChanged(updated))
        return StartHeatResult.Success(updated)
    }

    suspend fun finish(id: UUID, actorId: UUID): FinishHeatResult {
        val heat = heatRepository.findById(id)
            ?: return FinishHeatResult.NotFound

        if (heat.status != HeatStatus.STARTED) {
            return FinishHeatResult.InvalidStatus(heat.status)
        }

        val now = clock.now()
        heatRepository.updateStatus(id, HeatStatus.FINISHED, finishedAt = now)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_FINISHED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat finished",
                occurredAt = clock.now(),
            ),
        )

        val updated = heatRepository.findById(id)!!
        _events.tryEmit(HeatServiceEvent.HeatStateChanged(updated))
        return FinishHeatResult.Success(updated)
    }

    suspend fun cancel(id: UUID, actorId: UUID): CancelHeatResult {
        val heat = heatRepository.findById(id)
            ?: return CancelHeatResult.NotFound

        if (heat.status != HeatStatus.ARMED && heat.status != HeatStatus.STARTED) {
            return CancelHeatResult.InvalidStatus(heat.status)
        }

        measurementGateway.cancel(id)
        heatRepository.updateStatus(id, HeatStatus.CANCELLED)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_CANCELLED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat cancelled",
                occurredAt = clock.now(),
            ),
        )

        val updated = heatRepository.findById(id)!!
        _events.tryEmit(HeatServiceEvent.HeatStateChanged(updated))
        return CancelHeatResult.Success(updated)
    }

    suspend fun acceptResult(id: UUID, actorId: UUID): AcceptResult {
        val heat = heatRepository.findById(id)
            ?: return AcceptResult.NotFound

        if (heat.status != HeatStatus.FINISHED && heat.status != HeatStatus.TIMEOUT) {
            return AcceptResult.InvalidStatus(heat.status)
        }

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_RESULT_ACCEPTED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat result accepted",
                occurredAt = clock.now(),
            ),
        )

        _events.tryEmit(HeatServiceEvent.HeatResultAccepted(id))
        return AcceptResult.Success
    }

    suspend fun rejectResult(id: UUID, actorId: UUID): RejectResult {
        val heat = heatRepository.findById(id)
            ?: return RejectResult.NotFound

        if (heat.status != HeatStatus.FINISHED && heat.status != HeatStatus.TIMEOUT) {
            return RejectResult.InvalidStatus(heat.status)
        }

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_RESULT_REJECTED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat result rejected",
                occurredAt = clock.now(),
            ),
        )

        _events.tryEmit(HeatServiceEvent.HeatResultRejected(id))
        return RejectResult.Success
    }

    suspend fun repeat(id: UUID, actorId: UUID): RepeatHeatResult {
        val heat = heatRepository.findById(id)
            ?: return RepeatHeatResult.NotFound

        heatRepository.updateStatus(id, HeatStatus.PLANNED)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_REPEATED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat marked for repeat",
                occurredAt = clock.now(),
            ),
        )

        val updated = heatRepository.findById(id)!!
        _events.tryEmit(HeatServiceEvent.HeatStateChanged(updated))
        return RepeatHeatResult.Success(updated)
    }

    suspend fun addMeasurement(heatId: UUID, lane: Int, durationNanos: Long, outcome: LaneOutcome, actorId: UUID): AddMeasurementResult {
        val heat = heatRepository.findById(heatId)
            ?: return AddMeasurementResult.NotFound

        if (heat.status != HeatStatus.STARTED) {
            return AddMeasurementResult.InvalidStatus(heat.status)
        }

        val now = clock.now()
        val measurement = Measurement(
            id = UUID.randomUUID(),
            heatId = heatId,
            lane = lane,
            durationNanos = durationNanos,
            outcome = outcome,
            receivedAt = now,
        )

        heatRepository.insertMeasurement(measurement)

        auditRepository.insert(
            AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "MEASUREMENT_ADDED",
                targetType = "Measurement",
                targetId = measurement.id,
                summary = "Measurement for lane $lane: ${durationNanos}ns, outcome=$outcome",
                occurredAt = now,
            ),
        )

        val updated = heatRepository.findById(heatId)!!
        _events.tryEmit(HeatServiceEvent.HeatStateChanged(updated))
        return AddMeasurementResult.Success(updated)
    }

    private suspend fun handleGatewayEvent(event: MeasurementGatewayEvent) {
        when (event) {
            is MeasurementGatewayEvent.HeatStarted -> {
                heatRepository.updateStatus(event.heatId, HeatStatus.STARTED, startedAt = clock.now())
                heatRepository.findById(event.heatId)?.let {
                    _events.emit(HeatServiceEvent.HeatStateChanged(it))
                }
            }
            is MeasurementGatewayEvent.LaneFinished -> {
                val measurement = Measurement(
                    id = UUID.randomUUID(),
                    heatId = event.heatId,
                    lane = event.lane,
                    durationNanos = event.durationNanos,
                    outcome = event.outcome,
                    receivedAt = clock.now(),
                )
                heatRepository.insertMeasurement(measurement)
                heatRepository.findById(event.heatId)?.let {
                    _events.emit(HeatServiceEvent.HeatStateChanged(it))
                }
            }
            is MeasurementGatewayEvent.HeatFinished -> {
                heatRepository.updateStatus(event.heatId, HeatStatus.FINISHED, finishedAt = clock.now())
                heatRepository.findById(event.heatId)?.let {
                    _events.emit(HeatServiceEvent.HeatStateChanged(it))
                }
            }
            is MeasurementGatewayEvent.HeatTimeout -> {
                heatRepository.updateStatus(event.heatId, HeatStatus.TIMEOUT, finishedAt = clock.now())
                heatRepository.findById(event.heatId)?.let {
                    _events.emit(HeatServiceEvent.HeatStateChanged(it))
                }
            }
            is MeasurementGatewayEvent.Error -> {
                heatRepository.updateStatus(event.heatId, HeatStatus.TECHNICAL_ERROR)
                heatRepository.findById(event.heatId)?.let {
                    _events.emit(HeatServiceEvent.HeatStateChanged(it))
                }
            }
        }
    }
}

