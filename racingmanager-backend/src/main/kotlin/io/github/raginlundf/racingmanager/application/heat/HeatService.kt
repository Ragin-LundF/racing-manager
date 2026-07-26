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
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaspberryPiMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Clock

@Suppress("TooManyFunctions")
class HeatService(
    private val heatRepository: HeatRepository,
    private val eventRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val auditRepository: AuditRepository,
    private val measurementGateway: MeasurementGateway = RaspberryPiMeasurementGateway.simulated(),
) {
    private val clock: Clock = Clock.System
    private val scope = CoroutineScope(context = Dispatchers.Default)
    private val _events = MutableSharedFlow<HeatServiceEvent>(extraBufferCapacity = 64)

    val events = _events.asSharedFlow()

    init {
        scope.launch {
            measurementGateway.events().collect { event ->
                handleGatewayEvent(event = event)
            }
        }
    }

    /** The device to drive for the event's timing mode, or null for MANUAL, which has
        no device at all. SIMULATED events are routed to the simulator by the gateway
        itself, so they never fail on missing hardware. */
    private fun gatewayFor(eventId: UUID): MeasurementGateway? {
        val measurementType = eventRepository.findById(id = eventId)?.settings?.measurementType
            ?: return measurementGateway
        if (measurementType == MeasurementType.MANUAL) {
            return null
        }
        return measurementGateway.forMeasurementType(measurementType = measurementType)
    }

    fun findByEventId(eventId: UUID): List<HeatEntity> {
        return heatRepository.findByEventId(eventId = eventId)
    }

    fun findById(id: UUID): HeatEntity? {
        return heatRepository.findById(id = id)
    }

    fun findLatestByEventId(eventId: UUID): HeatEntity? {
        return heatRepository.findLatestByEventId(eventId = eventId)
    }

    fun create(eventId: UUID, participantIds: List<UUID>, actorId: UUID): CreateHeatResult {
        val event = eventRepository.findById(id = eventId)
            ?: return CreateHeatResult.EventNotFound

        if (event.status != EventStatus.ACTIVE) {
            return CreateHeatResult.EventNotActive
        }

        val participants = participantIds.mapNotNull { participantRepository.findById(id = it) }
        if (participants.size != participantIds.size) {
            return CreateHeatResult.ParticipantNotFound
        }

        if (participants.any { it.status != ParticipantStatus.ACTIVE }) {
            return CreateHeatResult.ParticipantNotActive
        }

        val existingHeats = heatRepository.findByEventId(eventId = eventId)
        val round = 1
        // Per-phase counter: count only this round's heats so qualification numbers from #1.
        val heatNumber = existingHeats.count { it.round == round } + 1

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

        heatRepository.insert(heat = heat)

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_CREATED",
                targetType = "Heat",
                targetId = heat.id,
                summary = "Heat #$heatNumber created with ${participants.size} participants",
                occurredAt = now,
            ),
        )

        _events.tryEmit(value = HeatServiceEvent.HeatCreated(heat = heat))
        return CreateHeatResult.Success(heat = heat)
    }

    suspend fun arm(id: UUID, actorId: UUID): ArmHeatResult {
        val heat = heatRepository.findById(id = id)
            ?: return ArmHeatResult.NotFound

        if (heat.status != HeatStatus.PLANNED) {
            return ArmHeatResult.InvalidStatus(current = heat.status)
        }

        val now = clock.now()
        heatRepository.updateStatus(id = id, status = HeatStatus.ARMED, armedAt = now)

        // Manual timing has no device to prepare — the operator enters times later.
        val gateway = gatewayFor(eventId = heat.eventId)
        if (gateway != null) {
            val result = gateway.arm(heat = heat)
            if (result is GatewayArmResult.Error) {
                heatRepository.updateStatus(id = id, status = HeatStatus.PLANNED)
                return ArmHeatResult.GatewayError(message = result.message)
            }
        }

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_ARMED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat armed",
                occurredAt = clock.now(),
            ),
        )

        val updated = heatRepository.findById(id = id)!!
        _events.tryEmit(value = HeatServiceEvent.HeatStateChanged(heat = updated))
        return ArmHeatResult.Success(heat = updated)
    }

    suspend fun start(id: UUID, actorId: UUID): StartHeatResult {
        val heat = heatRepository.findById(id = id)
            ?: return StartHeatResult.NotFound

        if (heat.status != HeatStatus.ARMED) {
            return StartHeatResult.InvalidStatus(current = heat.status)
        }

        val now = clock.now()
        heatRepository.updateStatus(id = id, status = HeatStatus.STARTED, startedAt = now)

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_STARTED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat started",
                occurredAt = clock.now(),
            ),
        )

        val updated = heatRepository.findById(id = id)!!
        // Release the gate / begin timing on the device. Manual timing has none.
        gatewayFor(eventId = updated.eventId)?.start(heat = updated)

        _events.tryEmit(value = HeatServiceEvent.HeatStateChanged(heat = updated))
        return StartHeatResult.Success(heat = updated)
    }

    suspend fun finish(id: UUID, actorId: UUID): FinishHeatResult {
        val heat = heatRepository.findById(id = id)
            ?: return FinishHeatResult.NotFound

        if (heat.status != HeatStatus.STARTED) {
            return FinishHeatResult.InvalidStatus(current = heat.status)
        }

        val now = clock.now()
        heatRepository.updateStatus(id = id, status = HeatStatus.FINISHED, finishedAt = now)

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_FINISHED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat finished",
                occurredAt = clock.now(),
            ),
        )

        val updated = heatRepository.findById(id = id)!!
        _events.tryEmit(value = HeatServiceEvent.HeatStateChanged(heat = updated))
        return FinishHeatResult.Success(heat = updated)
    }

    suspend fun cancel(id: UUID, actorId: UUID): CancelHeatResult {
        val heat = heatRepository.findById(id = id)
            ?: return CancelHeatResult.NotFound

        if (heat.status != HeatStatus.ARMED && heat.status != HeatStatus.STARTED) {
            return CancelHeatResult.InvalidStatus(current = heat.status)
        }

        gatewayFor(eventId = heat.eventId)?.cancel(heatId = id)
        heatRepository.updateStatus(id = id, status = HeatStatus.CANCELLED)

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_CANCELLED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat cancelled",
                occurredAt = clock.now(),
            ),
        )

        val updated = heatRepository.findById(id = id)!!
        _events.tryEmit(value = HeatServiceEvent.HeatStateChanged(heat = updated))
        return CancelHeatResult.Success(heat = updated)
    }

    suspend fun acceptResult(id: UUID, actorId: UUID): AcceptResult {
        val heat = heatRepository.findById(id = id)
            ?: return AcceptResult.NotFound

        if (heat.status != HeatStatus.FINISHED && heat.status != HeatStatus.TIMEOUT) {
            return AcceptResult.InvalidStatus(current = heat.status)
        }

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_RESULT_ACCEPTED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat result accepted",
                occurredAt = clock.now(),
            ),
        )

        heatRepository.updateStatus(id = id, status = HeatStatus.ACCEPTED)

        val updated = heatRepository.findById(id = id)!!
        _events.tryEmit(value = HeatServiceEvent.HeatResultAccepted(heatId = id))
        _events.tryEmit(value = HeatServiceEvent.HeatStateChanged(heat = updated))
        return AcceptResult.Success
    }

    suspend fun rejectResult(id: UUID, actorId: UUID): RejectResult {
        val heat = heatRepository.findById(id = id)
            ?: return RejectResult.NotFound

        if (heat.status != HeatStatus.FINISHED && heat.status != HeatStatus.TIMEOUT) {
            return RejectResult.InvalidStatus(current = heat.status)
        }

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_RESULT_REJECTED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat result rejected",
                occurredAt = clock.now(),
            ),
        )

        heatRepository.updateStatus(id = id, status = HeatStatus.REJECTED)

        val updated = heatRepository.findById(id = id)!!
        _events.tryEmit(value = HeatServiceEvent.HeatResultRejected(heatId = id))
        _events.tryEmit(value = HeatServiceEvent.HeatStateChanged(heat = updated))
        return RejectResult.Success
    }

    suspend fun repeat(id: UUID, actorId: UUID): RepeatHeatResult {
        heatRepository.findById(id = id)
            ?: return RepeatHeatResult.NotFound

        heatRepository.reopenForRepeat(id = id)

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "HEAT_REPEATED",
                targetType = "Heat",
                targetId = id,
                summary = "Heat marked for repeat",
                occurredAt = clock.now(),
            ),
        )

        val updated = heatRepository.findById(id = id)!!
        _events.tryEmit(value = HeatServiceEvent.HeatStateChanged(heat = updated))
        return RepeatHeatResult.Success(heat = updated)
    }

    suspend fun addMeasurement(
        heatId: UUID,
        lane: Int,
        durationNanos: Long,
        outcome: LaneOutcome,
        actorId: UUID
    ): AddMeasurementResult {
        val heat = heatRepository.findById(id = heatId)
            ?: return AddMeasurementResult.NotFound

        if (heat.status != HeatStatus.STARTED) {
            return AddMeasurementResult.InvalidStatus(current = heat.status)
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

        heatRepository.insertMeasurement(measurement = measurement)

        auditRepository.insert(
            entry = AuditEntryEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                action = "MEASUREMENT_ADDED",
                targetType = "Measurement",
                targetId = measurement.id,
                summary = "Measurement for lane $lane: ${durationNanos}ns, outcome=$outcome",
                occurredAt = now,
            ),
        )

        val updated = heatRepository.findById(id = heatId)!!
        _events.tryEmit(value = HeatServiceEvent.HeatStateChanged(heat = updated))
        return AddMeasurementResult.Success(heat = updated)
    }

    private suspend fun handleGatewayEvent(event: MeasurementGatewayEvent) {
        when (event) {
            is MeasurementGatewayEvent.HeatStarted -> {
                heatRepository.updateStatus(id = event.heatId, status = HeatStatus.STARTED, startedAt = clock.now())
                emitStateChanged(heatId = event.heatId)
            }

            is MeasurementGatewayEvent.LaneFinished -> {
                // Duplicate-finish guard: at most one accepted finish per lane and
                // race (raspberry.md §7), even if a frame is redelivered.
                val current = heatRepository.findById(id = event.heatId)
                if (current != null && current.measurements.any { it.lane == event.lane }) {
                    return
                }
                val measurement = Measurement(
                    id = UUID.randomUUID(),
                    heatId = event.heatId,
                    lane = event.lane,
                    durationNanos = event.durationNanos,
                    outcome = event.outcome,
                    receivedAt = clock.now(),
                )
                heatRepository.insertMeasurement(measurement = measurement)
                emitStateChanged(heatId = event.heatId)
            }

            is MeasurementGatewayEvent.HeatFinished -> {
                heatRepository.updateStatus(id = event.heatId, status = HeatStatus.FINISHED, finishedAt = clock.now())
                emitStateChanged(heatId = event.heatId)
            }

            is MeasurementGatewayEvent.HeatTimeout -> {
                heatRepository.updateStatus(id = event.heatId, status = HeatStatus.TIMEOUT, finishedAt = clock.now())
                emitStateChanged(heatId = event.heatId)
            }

            is MeasurementGatewayEvent.Error -> {
                heatRepository.updateStatus(id = event.heatId, status = HeatStatus.TECHNICAL_ERROR)
                emitStateChanged(heatId = event.heatId)
            }
        }
    }

    private suspend fun emitStateChanged(heatId: UUID) {
        heatRepository.findById(id = heatId)?.let { heat ->
            _events.emit(value = HeatServiceEvent.HeatStateChanged(heat = heat))
        }
    }
}
