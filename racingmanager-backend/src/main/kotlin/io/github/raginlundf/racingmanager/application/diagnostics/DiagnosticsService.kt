package io.github.raginlundf.racingmanager.application.diagnostics

import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.raginlundf.racingmanager.domain.heat.HeatStatus
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import kotlinx.coroutines.runBlocking
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import javax.sql.DataSource

class DiagnosticsService(
    private val dataSource: DataSource,
    private val eventRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val heatRepository: HeatRepository,
) {
    data class UnfinishedHeat(
        val heat: HeatEntity,
        val event: EventEntity,
    )

    data class RecoveryAction(
        val heatId: UUID,
        val action: String,
    )

    data class DiagnosticsBundle(
        val database: DatabaseStatus,
        val events: EventSummary,
        val unfinishedHeats: List<UnfinishedHeat>,
        val version: String,
    )

    data class DatabaseStatus(
        val connected: Boolean,
        val pingMs: Long,
    )

    data class EventSummary(
        val total: Int,
        val draft: Int,
        val active: Int,
        val completed: Int,
        val archived: Int,
        val totalParticipants: Int,
        val totalHeats: Int,
    )

    fun checkDatabase(): DatabaseStatus {
        val start = System.currentTimeMillis()
        return try {
            dataSource.connection.use { conn ->
                conn.isValid(2)
            }
            DatabaseStatus(connected = true, pingMs = System.currentTimeMillis() - start)
        } catch (e: Exception) {
            DatabaseStatus(connected = false, pingMs = System.currentTimeMillis() - start)
        }
    }

    /** Server-startup recovery check (design §J.1) — deliberately global
        (logged, never returned through a tenant-scoped API response) since it
        runs once before any request exists to scope it to. Every
        tenant-facing path below goes through [findUnfinishedHeatsForTenant]
        instead. */
    fun findUnfinishedHeats(): List<UnfinishedHeat> {
        val unfinishedStatuses = setOf(HeatStatus.ARMED, HeatStatus.STARTED)
        val allHeats = heatRepository.findAll()
        return allHeats
            .filter { it.status in unfinishedStatuses }
            .mapNotNull { heat ->
                eventRepository.findById(heat.eventId)?.let { event ->
                    UnfinishedHeat(heat, event)
                }
            }
    }

    /** Tenant-scoped equivalent of [findUnfinishedHeats] — walks only this
        tenant's own events rather than every heat in the database, so a
        `rm:admin` from one tenant can never see another tenant's unfinished
        heats (design §J.1). */
    private fun findUnfinishedHeatsForTenant(tenantId: UUID): List<UnfinishedHeat> {
        val unfinishedStatuses = setOf(HeatStatus.ARMED, HeatStatus.STARTED)
        return eventRepository.findAllForTenant(tenantId).flatMap { event ->
            heatRepository.findByEventId(event.id)
                .filter { it.status in unfinishedStatuses }
                .map { heat -> UnfinishedHeat(heat, event) }
        }
    }

    /** Rejects recovery of a heat belonging to another tenant (design §J.1) —
        returns null exactly as if the heat didn't exist, matching the
        no-cross-tenant-existence-disclosure convention used everywhere else
        in this codebase. */
    fun recoverHeat(heatId: UUID, action: String, tenantId: UUID): RecoveryAction? {
        val heat = heatRepository.findById(heatId) ?: return null
        eventRepository.findByIdForTenant(heat.eventId, tenantId) ?: return null
        return when (action) {
            "cancel" -> {
                heatRepository.updateStatus(heat.id, HeatStatus.CANCELLED)
                RecoveryAction(heatId, "cancelled")
            }
            "reset" -> {
                heatRepository.updateStatus(heat.id, HeatStatus.PLANNED)
                RecoveryAction(heatId, "reset_to_planned")
            }
            else -> null
        }
    }

    fun getBundle(tenantId: UUID): DiagnosticsBundle {
        val db = checkDatabase()
        val events = eventRepository.findAllForTenant(tenantId)
        val allParticipants = events.sumOf { event ->
            participantRepository.countByEventId(event.id)
        }
        val allHeats = events.sumOf { event -> heatRepository.findByEventId(event.id).size }

        return DiagnosticsBundle(
            database = db,
            events = EventSummary(
                total = events.size,
                draft = events.count { it.status == EventStatus.DRAFT },
                active = events.count { it.status == EventStatus.ACTIVE },
                completed = events.count { it.status == EventStatus.COMPLETED },
                archived = events.count { it.status == EventStatus.ARCHIVED },
                totalParticipants = allParticipants.toInt(),
                totalHeats = allHeats,
            ),
            unfinishedHeats = findUnfinishedHeatsForTenant(tenantId),
            version = "1.0-SNAPSHOT",
        )
    }
}