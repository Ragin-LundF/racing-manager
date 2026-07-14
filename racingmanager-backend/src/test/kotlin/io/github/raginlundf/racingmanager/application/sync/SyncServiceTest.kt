package io.github.raginlundf.racingmanager.application.sync

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
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

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
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        tenantRepository = tenantRepository,
        importedPackageRepository = ImportedPackageRepository(),
        localInstanceRepository = LocalInstanceRepository(),
        jwtKeyProvider = jwtKeyProvider,
    )
    private val service = SyncService(
        pairingCodeRepository = pairingCodeRepository,
        pairedInstanceRepository = pairedInstanceRepository,
        syncedResultRepository = syncedResultRepository,
        eventRepository = eventRepository,
        auditRepository = auditRepository
    )

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
        tenantRepository.insert(
            tenant = TenantEntity(
                id = tenantId,
                slug = "acme",
                displayName = "Acme Racing",
                createdAt = clock.now()
            )
        )
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
        val code = (service.issuePairingToken(tenantId = tenantId) as PairingTokenResult.Success).code
        val instanceId = UUID.randomUUID()

        val result = service.pair(pairingCode = code, localInstanceId = instanceId) as PairResult.Success
        assertEquals(expected = tenantId, actual = result.instance.tenantId)
        assertEquals(expected = PairedInstanceStatus.ACTIVE, actual = result.instance.status)
        assertEquals(expected = 1, actual = service.listInstances(tenantId = tenantId).size)
    }

    @Test
    fun `a pairing code can only be used once`() {
        val tenantId = seedTenant()
        val code = (service.issuePairingToken(tenantId = tenantId) as PairingTokenResult.Success).code

        service.pair(pairingCode = code, localInstanceId = UUID.randomUUID())
        val second = service.pair(pairingCode = code, localInstanceId = UUID.randomUUID())
        assertEquals(expected = PairResult.InvalidOrExpiredCode, actual = second)
    }

    @Test
    fun `an unknown pairing code is rejected`() {
        val result = service.pair(pairingCode = UUID.randomUUID(), localInstanceId = UUID.randomUUID())
        assertEquals(expected = PairResult.InvalidOrExpiredCode, actual = result)
    }

    @Test
    fun `a revoked instance cannot sync results`() {
        val tenantId = seedTenant()
        val eventId = seedEvent(tenantId = tenantId)
        val code = (service.issuePairingToken(tenantId = tenantId) as PairingTokenResult.Success).code
        val instanceId = UUID.randomUUID()
        service.pair(pairingCode = code, localInstanceId = instanceId)
        service.revoke(tenantId = tenantId, instanceId = instanceId)

        localPackageService.issue(tenantId = tenantId, listOf(eventId))
        val result = service.syncResults(
            tenantId = tenantId,
            instanceId = instanceId,
            eventId = eventId,
            resultsJson = """{"ok":true}""",
            actorId = UUID.randomUUID()
        )
        assertEquals(expected = SyncResultsResult.InstanceRevoked, actual = result)
    }

    @Test
    fun `syncing results for an event that was never checked out is rejected`() {
        val tenantId = seedTenant()
        val eventId = seedEvent(tenantId = tenantId)
        val code = (service.issuePairingToken(tenantId = tenantId) as PairingTokenResult.Success).code
        val instanceId = UUID.randomUUID()
        service.pair(pairingCode = code, localInstanceId = instanceId)

        val result = service.syncResults(
            tenantId = tenantId,
            instanceId = instanceId,
            eventId = eventId,
            resultsJson = """{"ok":true}""",
            actorId = UUID.randomUUID()
        )
        assertEquals(expected = SyncResultsResult.EventNotLocked, actual = result)
    }

    @Test
    fun `syncing results unlocks the event, marks it synced, and records the instance's last sync time`() {
        val tenantId = seedTenant()
        val eventId = seedEvent(tenantId = tenantId)
        val code = (service.issuePairingToken(tenantId = tenantId) as PairingTokenResult.Success).code
        val instanceId = UUID.randomUUID()
        service.pair(pairingCode = code, localInstanceId = instanceId)

        localPackageService.issue(tenantId = tenantId, eventIds = listOf(eventId))
        val lockedEvent = eventRepository.findByIdForTenant(eventId, tenantId)!!
        assertEquals(expected = true, actual = lockedEvent.lockedForSync)
        assertEquals(expected = SyncStatus.SYNC_PENDING, actual = lockedEvent.syncStatus)

        val actorId = UUID.randomUUID()
        val result = service.syncResults(
            tenantId = tenantId,
            instanceId = instanceId,
            eventId = eventId,
            resultsJson = """{"heats":[]}""",
            actorId = actorId
        ) as SyncResultsResult.Success
        assertNotNull(actual = result.syncedResultId)

        val syncedEvent = eventRepository.findByIdForTenant(id = eventId, tenantId = tenantId)!!
        assertEquals(expected = false, actual = syncedEvent.lockedForSync)
        assertEquals(expected = SyncStatus.SYNCED, actual = syncedEvent.syncStatus)

        val instance = pairedInstanceRepository.findById(id = instanceId)!!
        assertNotNull(actual = instance.lastSyncAt)
    }

    @Test
    fun `pairing code is rejected once expired`() {
        val tenantId = seedTenant()
        val expiredService = SyncService(
            pairingCodeRepository,
            pairedInstanceRepository,
            syncedResultRepository,
            eventRepository,
            auditRepository,
            pairingCodeTtl = (-1).seconds
        )
        val code = (expiredService.issuePairingToken(tenantId = tenantId) as PairingTokenResult.Success).code

        val result = service.pair(pairingCode = code, localInstanceId = UUID.randomUUID())
        assertEquals(expected = PairResult.InvalidOrExpiredCode, actual = result)
    }
}
