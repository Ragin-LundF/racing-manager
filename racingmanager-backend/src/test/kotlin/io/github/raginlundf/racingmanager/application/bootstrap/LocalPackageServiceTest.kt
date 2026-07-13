package io.github.raginlundf.racingmanager.application.bootstrap

import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity
import io.github.raginlundf.racingmanager.domain.participant.ParticipantStatus
import io.github.raginlundf.racingmanager.domain.participant.VehicleEntity
import io.github.raginlundf.racingmanager.domain.tenant.TenantEntity
import io.github.raginlundf.racingmanager.infrastructure.DatabaseTestHelper
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ImportedPackageRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.LocalInstanceRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.TenantRepository
import io.github.raginlundf.racingmanager.infrastructure.security.LocalJwtKeyProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import java.util.UUID

/** Slice H.4: signature/integrity verification, expiry, single-use/idempotent
    re-import, and transactional import — all against the caller's own tenant
    only (issuance and import never accept a tenant-selection parameter, so
    there is no cross-tenant import surface to attack). */
class LocalPackageServiceTest {

    private val jwtKeyProvider = LocalJwtKeyProvider(SigningKeyRepository())
    private val eventRepository = EventRepository()
    private val participantRepository = ParticipantRepository()
    private val tenantRepository = TenantRepository()
    private val importedPackageRepository = ImportedPackageRepository()
    private val localInstanceRepository = LocalInstanceRepository()
    private val clock = Clock.System

    private val service = LocalPackageService(
        eventRepository, participantRepository, tenantRepository, importedPackageRepository, localInstanceRepository, jwtKeyProvider,
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

    private fun seedSourceTenantWithEvent(): Pair<UUID, UUID> {
        val sourceTenantId = UUID.randomUUID()
        val now = clock.now()
        tenantRepository.insert(TenantEntity(id = sourceTenantId, slug = "acme", displayName = "Acme Racing", createdAt = now))
        val actorId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        eventRepository.insert(
            EventEntity(
                id = eventId, tenantId = sourceTenantId, name = "Spring Cup", status = EventStatus.DRAFT,
                settings = EventSettings(), createdBy = actorId, createdAt = now,
            ),
        )
        participantRepository.insert(
            ParticipantEntity(
                id = UUID.randomUUID(), eventId = eventId, startNumber = 1, firstName = "Alice", lastName = "Smith",
                status = ParticipantStatus.ACTIVE, createdAt = now,
                vehicle = VehicleEntity(id = UUID.randomUUID(), participantId = UUID.randomUUID(), name = "Racer 1"),
            ),
        )
        return sourceTenantId to eventId
    }

    /** In real deployment, hosted and local instances are separate databases —
        the artifact is what travels between them. This test suite shares one
        database, so once an artifact has been issued from the "source"
        event, this simulates it now living independently by removing the
        source rows before import — otherwise re-inserting the same event id
        under a different tenant would collide with the still-present source
        row's primary key. */
    private fun detachFromSource(eventId: UUID) {
        participantRepository.deleteByEventId(eventId)
        eventRepository.delete(eventId)
    }

    @Test
    fun `issues a signed artifact and imports it into the caller's own tenant`() {
        val (sourceTenantId, eventId) = seedSourceTenantWithEvent()
        val artifact = (service.issue(sourceTenantId, listOf(eventId)) as IssueResult.Success).artifact
        detachFromSource(eventId)

        val localTenantId = AuthService.LOCAL_TENANT_ID
        val importerId = UUID.randomUUID()
        val result = service.import(artifact, localTenantId, importerId, dryRun = false) as ImportResult.Success

        assertEquals(false, result.alreadyImported)
        assertEquals(listOf(eventId), result.importedEventIds)
        assertNotNull(result.localInstanceId)

        val imported = eventRepository.findByIdForTenant(eventId, localTenantId)
        assertNotNull(imported)
        assertEquals(sourceTenantId, imported.originTenantId)
        val participants = participantRepository.findByEventId(eventId)
        assertEquals(1, participants.size)
        assertEquals("Alice", participants.first().firstName)
    }

    @Test
    fun `rejects an artifact whose payload was tampered with after signing`() {
        val (sourceTenantId, eventId) = seedSourceTenantWithEvent()
        val artifact = (service.issue(sourceTenantId, listOf(eventId)) as IssueResult.Success).artifact

        val tamperedBytes = java.util.Base64.getDecoder().decode(artifact.payload)
        tamperedBytes[tamperedBytes.size / 2] = (tamperedBytes[tamperedBytes.size / 2] + 1).toByte()
        val tampered = artifact.copy(payload = java.util.Base64.getEncoder().encodeToString(tamperedBytes))

        val result = service.import(tampered, AuthService.LOCAL_TENANT_ID, UUID.randomUUID(), dryRun = false)
        assertEquals(ImportResult.InvalidSignature, result)
    }

    @Test
    fun `rejects a malformed artifact`() {
        val artifact = io.github.raginlundf.racingmanager.api.bootstrap.models.LocalPackageArtifact(
            payload = "not-base64!!", signature = "also-not-base64!!", kid = "x", publicKey = "not-a-key",
        )
        val result = service.import(artifact, AuthService.LOCAL_TENANT_ID, UUID.randomUUID(), dryRun = false)
        assertEquals(ImportResult.InvalidArtifact, result)
    }

    @Test
    fun `rejects an expired package`() {
        val (sourceTenantId, eventId) = seedSourceTenantWithEvent()
        val expiredService = LocalPackageService(
            eventRepository, participantRepository, tenantRepository, importedPackageRepository, localInstanceRepository, jwtKeyProvider,
            packageTtl = (-1).seconds,
        )
        val artifact = (expiredService.issue(sourceTenantId, listOf(eventId)) as IssueResult.Success).artifact

        val result = service.import(artifact, AuthService.LOCAL_TENANT_ID, UUID.randomUUID(), dryRun = false)
        assertEquals(ImportResult.Expired, result)
    }

    @Test
    fun `re-importing the same package is idempotent and does not duplicate data`() {
        val (sourceTenantId, eventId) = seedSourceTenantWithEvent()
        val artifact = (service.issue(sourceTenantId, listOf(eventId)) as IssueResult.Success).artifact
        detachFromSource(eventId)
        val localTenantId = AuthService.LOCAL_TENANT_ID

        val first = service.import(artifact, localTenantId, UUID.randomUUID(), dryRun = false) as ImportResult.Success
        assertEquals(false, first.alreadyImported)

        val second = service.import(artifact, localTenantId, UUID.randomUUID(), dryRun = false) as ImportResult.Success
        assertEquals(true, second.alreadyImported)
        assertEquals(first.importedEventIds, second.importedEventIds)

        assertEquals(1, participantRepository.findByEventId(eventId).size)
    }

    @Test
    fun `dry run previews the import without writing any data`() {
        val (sourceTenantId, eventId) = seedSourceTenantWithEvent()
        val artifact = (service.issue(sourceTenantId, listOf(eventId)) as IssueResult.Success).artifact
        val localTenantId = AuthService.LOCAL_TENANT_ID

        val preview = service.import(artifact, localTenantId, UUID.randomUUID(), dryRun = true) as ImportResult.Preview
        assertEquals(false, preview.alreadyImported)
        assertEquals(listOf(eventId), preview.importedEventIds)
        assertEquals("Acme Racing", preview.originTenantDisplayName)

        assertEquals(null, eventRepository.findByIdForTenant(eventId, localTenantId))
    }

    @Test
    fun `import always lands on the caller's own tenant regardless of the package's origin tenant`() {
        val (sourceTenantId, eventId) = seedSourceTenantWithEvent()
        val artifact = (service.issue(sourceTenantId, listOf(eventId)) as IssueResult.Success).artifact
        detachFromSource(eventId)

        val callerTenantId = UUID.randomUUID()
        tenantRepository.insert(TenantEntity(id = callerTenantId, slug = "other-local", displayName = "Other Local", createdAt = clock.now()))

        val result = service.import(artifact, callerTenantId, UUID.randomUUID(), dryRun = false) as ImportResult.Success
        assertEquals(callerTenantId, result.tenantId)
        assertTrue(eventRepository.findByIdForTenant(eventId, callerTenantId) != null)
        assertTrue(eventRepository.findByIdForTenant(eventId, sourceTenantId) == null)
    }
}
