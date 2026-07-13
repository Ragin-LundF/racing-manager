package io.github.raginlundf.racingmanager.application.sync

import io.github.raginlundf.racingmanager.application.bootstrap.IssueResult
import io.github.raginlundf.racingmanager.application.bootstrap.LocalPackageService
import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.event.SyncStatus
import io.github.raginlundf.racingmanager.domain.sync.PairedInstanceStatus
import io.github.raginlundf.racingmanager.domain.tenant.TenantEntity
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ImportedPackageRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.LocalInstanceRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.PairedInstanceRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.PairingCodeRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SyncedResultRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.TenantRepository
import io.github.raginlundf.racingmanager.infrastructure.security.LocalJwtKeyProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import java.util.UUID

/** Slice I.6: pairing round trip, invalid/expired pairing codes, revoke, and
    results upload-back — including the precondition that an event must have
    been checked out (locked) before results can be synced for it. */
class SyncServiceTest {

    private val jwtKeyProvider = LocalJwtKeyProvider(SigningKeyRepository())
    private val eventRepository = EventRepository()
    private val participantRepository = ParticipantRepository()
    private val tenantRepository = TenantRepository()
    private val auditRepository = AuditRepository()
    private val pairingCodeRepository = PairingCodeRepository()
    private val pairedInstanceRepository = PairedInstanceRepository()
    private val syncedResultRepository = SyncedResultRepository()
    private val clock = Clock.System

    private val localPackageService = LocalPackageService(
        eventRepository, participantRepository, tenantRepository, ImportedPackageRepository(), LocalInstanceRepository(), jwtKeyProvider,
    )
    private val service = SyncService(pairingCodeRepository, pairedInstanceRepository, syncedResultRepository, eventRepository, auditRepository)

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.setUp()
        jwtKeyProvider.ensureKeyExists()
    }

    @AfterTest
    fun tearDown() {
        DatabaseTestHelper.tearDown()
    }

    private fun seedTenant(): UUID {
        val tenantId = UUID.randomUUID()
        tenantRepository.insert(TenantEntity(id = tenantId, slug = "acme", displayName = "Acme Racing", createdAt = clock.now()))
        return tenantId
    }

    private fun seedEvent(tenantId: UUID): UUID {
        val eventId = UUID.randomUUID()
        eventRepository.insert(
            EventEntity(
                id = eventId, tenantId = tenantId, name = "Spring Cup", status = EventStatus.DRAFT,
                settings = EventSettings(), createdBy = UUID.randomUUID(), createdAt = clock.now(),
            ),
        )
        return eventId
    }

    @Test
    fun `pairing a fresh local instance registers it as active`() {
        val tenantId = seedTenant()
        val code = (service.issuePairingToken(tenantId) as PairingTokenResult.Success).code
        val instanceId = UUID.randomUUID()

        val result = service.pair(code, instanceId) as PairResult.Success
        assertEquals(tenantId, result.instance.tenantId)
        assertEquals(PairedInstanceStatus.ACTIVE, result.instance.status)
        assertEquals(1, service.listInstances(tenantId).size)
    }

    @Test
    fun `a pairing code can only be used once`() {
        val tenantId = seedTenant()
        val code = (service.issuePairingToken(tenantId) as PairingTokenResult.Success).code

        service.pair(code, UUID.randomUUID())
        val second = service.pair(code, UUID.randomUUID())
        assertEquals(PairResult.InvalidOrExpiredCode, second)
    }

    @Test
    fun `an unknown pairing code is rejected`() {
        val result = service.pair(UUID.randomUUID(), UUID.randomUUID())
        assertEquals(PairResult.InvalidOrExpiredCode, result)
    }

    @Test
    fun `a revoked instance cannot sync results`() {
        val tenantId = seedTenant()
        val eventId = seedEvent(tenantId)
        val code = (service.issuePairingToken(tenantId) as PairingTokenResult.Success).code
        val instanceId = UUID.randomUUID()
        service.pair(code, instanceId)
        service.revoke(tenantId, instanceId)

        localPackageService.issue(tenantId, listOf(eventId))
        val result = service.syncResults(tenantId, instanceId, eventId, """{"ok":true}""", UUID.randomUUID())
        assertEquals(SyncResultsResult.InstanceRevoked, result)
    }

    @Test
    fun `syncing results for an event that was never checked out is rejected`() {
        val tenantId = seedTenant()
        val eventId = seedEvent(tenantId)
        val code = (service.issuePairingToken(tenantId) as PairingTokenResult.Success).code
        val instanceId = UUID.randomUUID()
        service.pair(code, instanceId)

        val result = service.syncResults(tenantId, instanceId, eventId, """{"ok":true}""", UUID.randomUUID())
        assertEquals(SyncResultsResult.EventNotLocked, result)
    }

    @Test
    fun `syncing results unlocks the event, marks it synced, and records the instance's last sync time`() {
        val tenantId = seedTenant()
        val eventId = seedEvent(tenantId)
        val code = (service.issuePairingToken(tenantId) as PairingTokenResult.Success).code
        val instanceId = UUID.randomUUID()
        service.pair(code, instanceId)

        localPackageService.issue(tenantId, listOf(eventId))
        val lockedEvent = eventRepository.findByIdForTenant(eventId, tenantId)!!
        assertEquals(true, lockedEvent.lockedForSync)
        assertEquals(SyncStatus.SYNC_PENDING, lockedEvent.syncStatus)

        val actorId = UUID.randomUUID()
        val result = service.syncResults(tenantId, instanceId, eventId, """{"heats":[]}""", actorId) as SyncResultsResult.Success
        assertNotNull(result.syncedResultId)

        val syncedEvent = eventRepository.findByIdForTenant(eventId, tenantId)!!
        assertEquals(false, syncedEvent.lockedForSync)
        assertEquals(SyncStatus.SYNCED, syncedEvent.syncStatus)

        val instance = pairedInstanceRepository.findById(instanceId)!!
        assertNotNull(instance.lastSyncAt)
    }

    @Test
    fun `pairing code is rejected once expired`() {
        val tenantId = seedTenant()
        val expiredService = SyncService(pairingCodeRepository, pairedInstanceRepository, syncedResultRepository, eventRepository, auditRepository, pairingCodeTtl = (-1).seconds)
        val code = (expiredService.issuePairingToken(tenantId) as PairingTokenResult.Success).code

        val result = service.pair(code, UUID.randomUUID())
        assertEquals(PairResult.InvalidOrExpiredCode, result)
    }
}
