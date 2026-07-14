package io.github.raginlundf.racingmanager.application.bootstrap

import io.github.raginlundf.racingmanager.api.bootstrap.models.LocalPackageArtifact
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
import java.util.Base64
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Slice H.4: signature/integrity verification, expiry, single-use/idempotent
re-import, and transactional import — all against the caller's own tenant
only (issuance and import never accept a tenant-selection parameter, so
there is no cross-tenant import surface to attack). */
class LocalPackageServiceTest {

    private val jwtKeyProvider = LocalJwtKeyProvider(repository = SigningKeyRepository())
    private val eventRepository = EventRepository()
    private val participantRepository = ParticipantRepository()
    private val tenantRepository = TenantRepository()
    private val importedPackageRepository = ImportedPackageRepository()
    private val localInstanceRepository = LocalInstanceRepository()
    private val clock = Clock.System

    private val service = LocalPackageService(
        eventRepository = eventRepository,
        participantRepository = participantRepository,
        tenantRepository = tenantRepository,
        importedPackageRepository = importedPackageRepository,
        localInstanceRepository = localInstanceRepository,
        jwtKeyProvider = jwtKeyProvider,
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
        tenantRepository.insert(
            tenant = TenantEntity(
                id = sourceTenantId,
                slug = "acme",
                displayName = "Acme Racing",
                createdAt = now
            )
        )
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
        participantRepository.deleteByEventId(eventId = eventId)
        eventRepository.delete(id = eventId)
    }

    @Test
    fun `issues a signed artifact and imports it into the caller's own tenant`() {
        val (sourceTenantId, eventId) = seedSourceTenantWithEvent()
        val artifact = (service.issue(
            tenantId = sourceTenantId,
            eventIds = listOf(eventId)
        ) as IssueResult.Success).artifact
        detachFromSource(eventId = eventId)

        val localTenantId = AuthService.LOCAL_TENANT_ID
        val importerId = UUID.randomUUID()
        val result = service.import(
            artifact = artifact,
            targetTenantId = localTenantId,
            importedByUserId = importerId,
            dryRun = false
        ) as ImportResult.Success

        assertEquals(expected = false, actual = result.alreadyImported)
        assertEquals(expected = listOf(eventId), actual = result.importedEventIds)
        assertNotNull(actual = result.localInstanceId)

        val imported = eventRepository.findByIdForTenant(id = eventId, tenantId = localTenantId)
        assertNotNull(actual = imported)
        assertEquals(expected = sourceTenantId, actual = imported.originTenantId)
        val participants = participantRepository.findByEventId(eventId = eventId)
        assertEquals(expected = 1, actual = participants.size)
        assertEquals(expected = "Alice", actual = participants.first().firstName)
    }

    @Test
    fun `rejects an artifact whose payload was tampered with after signing`() {
        val (sourceTenantId, eventId) = seedSourceTenantWithEvent()
        val artifact = (service.issue(
            tenantId = sourceTenantId,
            eventIds = listOf(eventId)
        ) as IssueResult.Success).artifact

        val tamperedBytes = Base64.getDecoder().decode(artifact.payload)
        tamperedBytes[tamperedBytes.size / 2] = (tamperedBytes[tamperedBytes.size / 2] + 1).toByte()
        val tampered = artifact.copy(payload = Base64.getEncoder().encodeToString(tamperedBytes))

        val result = service.import(
            artifact = tampered,
            targetTenantId = AuthService.LOCAL_TENANT_ID,
            importedByUserId = UUID.randomUUID(),
            dryRun = false
        )
        assertEquals(expected = ImportResult.InvalidSignature, actual = result)
    }

    @Test
    fun `rejects a malformed artifact`() {
        val artifact = LocalPackageArtifact(
            payload = "not-base64!!", signature = "also-not-base64!!", kid = "x", publicKey = "not-a-key",
        )
        val result = service.import(
            artifact = artifact,
            targetTenantId = AuthService.LOCAL_TENANT_ID,
            importedByUserId = UUID.randomUUID(),
            dryRun = false
        )
        assertEquals(expected = ImportResult.InvalidArtifact, actual = result)
    }

    @Test
    fun `rejects an expired package`() {
        val (sourceTenantId, eventId) = seedSourceTenantWithEvent()
        val expiredService = LocalPackageService(
            eventRepository,
            participantRepository,
            tenantRepository,
            importedPackageRepository,
            localInstanceRepository,
            jwtKeyProvider,
            packageTtl = (-1).seconds,
        )
        val artifact = (expiredService.issue(
            tenantId = sourceTenantId,
            eventIds = listOf(eventId)
        ) as IssueResult.Success).artifact

        val result = service.import(
            artifact = artifact,
            targetTenantId = AuthService.LOCAL_TENANT_ID,
            importedByUserId = UUID.randomUUID(),
            dryRun = false
        )
        assertEquals(expected = ImportResult.Expired, actual = result)
    }

    @Test
    fun `re-importing the same package is idempotent and does not duplicate data`() {
        val (sourceTenantId, eventId) = seedSourceTenantWithEvent()
        val artifact = (service.issue(
            tenantId = sourceTenantId,
            eventIds = listOf(eventId)
        ) as IssueResult.Success).artifact
        detachFromSource(eventId = eventId)
        val localTenantId = AuthService.LOCAL_TENANT_ID

        val first = service.import(
            artifact = artifact,
            targetTenantId = localTenantId,
            importedByUserId = UUID.randomUUID(),
            dryRun = false
        ) as ImportResult.Success
        assertEquals(expected = false, actual = first.alreadyImported)

        val second = service.import(
            artifact = artifact,
            targetTenantId = localTenantId,
            importedByUserId = UUID.randomUUID(),
            dryRun = false
        ) as ImportResult.Success
        assertEquals(expected = true, actual = second.alreadyImported)
        assertEquals(expected = first.importedEventIds, actual = second.importedEventIds)

        assertEquals(expected = 1, actual = participantRepository.findByEventId(eventId = eventId).size)
    }

    @Test
    fun `dry run previews the import without writing any data`() {
        val (sourceTenantId, eventId) = seedSourceTenantWithEvent()
        val artifact = (service.issue(
            tenantId = sourceTenantId,
            eventIds = listOf(eventId)
        ) as IssueResult.Success).artifact
        val localTenantId = AuthService.LOCAL_TENANT_ID

        val preview = service.import(
            artifact = artifact,
            targetTenantId = localTenantId,
            importedByUserId = UUID.randomUUID(),
            dryRun = true
        ) as ImportResult.Preview
        assertEquals(expected = false, actual = preview.alreadyImported)
        assertEquals(expected = listOf(eventId), actual = preview.importedEventIds)
        assertEquals(expected = "Acme Racing", actual = preview.originTenantDisplayName)

        assertEquals(
            expected = null,
            actual = eventRepository.findByIdForTenant(id = eventId, tenantId = localTenantId)
        )
    }

    @Test
    fun `import always lands on the caller's own tenant regardless of the package's origin tenant`() {
        val (sourceTenantId, eventId) = seedSourceTenantWithEvent()
        val artifact = (service.issue(
            tenantId = sourceTenantId,
            eventIds = listOf(eventId)
        ) as IssueResult.Success).artifact
        detachFromSource(eventId = eventId)

        val callerTenantId = UUID.randomUUID()
        tenantRepository.insert(
            TenantEntity(
                id = callerTenantId,
                slug = "other-local",
                displayName = "Other Local",
                createdAt = clock.now()
            )
        )

        val result = service.import(
            artifact = artifact,
            targetTenantId = callerTenantId,
            importedByUserId = UUID.randomUUID(),
            dryRun = false
        ) as ImportResult.Success
        assertEquals(expected = callerTenantId, actual = result.tenantId)
        assertNotNull(actual = eventRepository.findByIdForTenant(id = eventId, tenantId = callerTenantId))
        assertNull(actual = eventRepository.findByIdForTenant(id = eventId, tenantId = sourceTenantId))
    }
}
