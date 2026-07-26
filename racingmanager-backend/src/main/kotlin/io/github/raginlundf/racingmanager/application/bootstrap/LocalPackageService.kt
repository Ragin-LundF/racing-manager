package io.github.raginlundf.racingmanager.application.bootstrap

import io.github.raginlundf.racingmanager.api.bootstrap.models.LocalPackageArtifact
import io.github.raginlundf.racingmanager.api.bootstrap.models.LocalPackagePayload
import io.github.raginlundf.racingmanager.api.bootstrap.models.PackagedEvent
import io.github.raginlundf.racingmanager.api.bootstrap.models.PackagedParticipant
import io.github.raginlundf.racingmanager.domain.bootstrap.ImportedPackageEntity
import io.github.raginlundf.racingmanager.domain.bootstrap.LocalInstanceEntity
import io.github.raginlundf.racingmanager.domain.event.EventEntity
import io.github.raginlundf.racingmanager.domain.event.EventSettings
import io.github.raginlundf.racingmanager.domain.event.EventStatus
import io.github.raginlundf.racingmanager.domain.event.LaneType
import io.github.raginlundf.racingmanager.domain.event.MeasurementType
import io.github.raginlundf.racingmanager.domain.event.SyncStatus
import io.github.raginlundf.racingmanager.domain.participant.ParticipantEntity
import io.github.raginlundf.racingmanager.domain.participant.ParticipantStatus
import io.github.raginlundf.racingmanager.domain.participant.VehicleEntity
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ImportedPackageRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.LocalInstanceRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.TenantRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtKeyProvider
import io.github.raginlundf.racingmanager.infrastructure.security.SigningKey
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Hosted→local bootstrap package: issuance signs a self-contained artifact
    with the current JWT signing key (reusing that RSA keypair rather than
    introducing a second signing mechanism, design §H). Import verifies
    integrity against the artifact's own embedded public key — this instance
    has no relationship to the issuing tenant's keys, so it cannot check
    *authenticity* against a trust root; only that the payload was not
    corrupted or tampered with after signing. Both issuance and import always
    operate on the caller's own tenant (there is no tenant-selection
    parameter on either operation) — a token from tenant A cannot import a
    package into, or export one claiming to be, a different tenant. */
class LocalPackageService(
    private val eventRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val tenantRepository: TenantRepository,
    private val importedPackageRepository: ImportedPackageRepository,
    private val localInstanceRepository: LocalInstanceRepository,
    private val jwtKeyProvider: JwtKeyProvider,
    private val packageTtl: Duration = 30.days,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val clock = Clock.System

    fun issue(tenantId: UUID, eventIds: List<UUID>): IssueResult {
        val tenant = tenantRepository.findById(tenantId) ?: return IssueResult.EventNotFound
        val events = eventIds.map { id ->
            val event = eventRepository.findByIdForTenant(id, tenantId) ?: return IssueResult.EventNotFound
            val participants = participantRepository.findByEventId(id)
            PackagedEvent(
                id = event.id.toString(),
                name = event.name,
                description = event.description,
                status = event.status.name,
                laneType = event.settings.laneType.name,
                measurementType = event.settings.measurementType.name,
                maxParticipants = event.settings.maxParticipants,
                trackLength = event.settings.trackLength,
                participants = participants.map { p ->
                    PackagedParticipant(
                        id = p.id.toString(),
                        startNumber = p.startNumber,
                        firstName = p.firstName,
                        lastName = p.lastName,
                        club = p.club,
                        status = p.status.name,
                        sortOrder = p.sortOrder,
                        vehicleName = p.vehicle?.name,
                        vehicleCategory = p.vehicle?.category,
                    )
                },
            )
        }

        val now = clock.now()
        val payload = LocalPackagePayload(
            packageId = UUID.randomUUID().toString(),
            tenantId = tenant.id.toString(),
            tenantSlug = tenant.slug,
            tenantDisplayName = tenant.displayName,
            createdAt = now.toString(),
            expiresAt = now.plus(packageTtl).toString(),
            events = events,
        )

        checkoutExportedEvents(eventIds, tenantId, now)
        val payloadBytes = json.encodeToString(LocalPackagePayload.serializer(), payload).toByteArray(Charsets.UTF_8)

        // Local package export/import (design §I.3) always runs in DeploymentMode.LOCAL,
        // whose LocalJwtKeyProvider only ever issues SigningKey.Rsa keys — this is
        // independent of the JWT signing algorithm a hosted deployment might choose.
        val key = jwtKeyProvider.signingKey() as? SigningKey.Rsa
            ?: error("Local package signing requires an RSA signing key")
        val privateKey = requireNotNull(key.privateKey) { "Signing key '${key.kid}' has no private key material" }
        val signatureBytes = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(payloadBytes)
        }.sign()

        return IssueResult.Success(
            LocalPackageArtifact(
                payload = Base64.getEncoder().encodeToString(payloadBytes),
                signature = Base64.getEncoder().encodeToString(signatureBytes),
                kid = key.kid,
                publicKey = Base64.getEncoder().encodeToString(key.publicKey.encoded),
            ),
        )
    }

    // Check out each exported event (design §I.3): hosted edits are
    // rejected until the local instance syncs its results back.
    private fun checkoutExportedEvents(eventIds: List<UUID>, tenantId: UUID, now: kotlin.time.Instant) {
        eventIds.forEach { id ->
            val event = eventRepository.findByIdForTenant(id, tenantId) ?: return@forEach
            eventRepository.update(
                event.copy(
                    lockedForSync = true,
                    syncStatus = SyncStatus.SYNC_PENDING,
                    version = event.version + 1,
                    updatedAt = now,
                ),
            )
        }
    }

    fun import(
        artifact: LocalPackageArtifact,
        targetTenantId: UUID,
        importedByUserId: UUID,
        dryRun: Boolean,
    ): ImportResult {
        val decoded = decodeArtifact(artifact) ?: return ImportResult.InvalidArtifact
        if (!verifySignature(decoded)) return ImportResult.InvalidSignature
        val parsed = parsePayload(decoded.payloadBytes) ?: return ImportResult.InvalidArtifact
        if (clock.now() > parsed.expiresAt) return ImportResult.Expired
        return resolveImport(parsed, targetTenantId, importedByUserId, dryRun)
    }

    /** Base64-decodes the three artifact components; a null result means the
        artifact is structurally corrupt (invalid base64 or key material). */
    private fun decodeArtifact(artifact: LocalPackageArtifact): DecodedArtifact? {
        val payloadBytes = decodeBase64(artifact.payload) ?: return null
        val signatureBytes = decodeBase64(artifact.signature) ?: return null
        val publicKey = decodePublicKey(artifact.publicKey) ?: return null
        return DecodedArtifact(payloadBytes = payloadBytes, signatureBytes = signatureBytes, publicKey = publicKey)
    }

    private fun verifySignature(decoded: DecodedArtifact): Boolean {
        return runCatching {
            Signature.getInstance("SHA256withRSA").apply {
                initVerify(decoded.publicKey)
                update(decoded.payloadBytes)
            }.verify(decoded.signatureBytes)
        }.getOrDefault(false)
    }

    /** Deserializes the verified payload and parses its identifiers/timestamp;
        a null result means the payload is structurally invalid. */
    private fun parsePayload(payloadBytes: ByteArray): ParsedPackage? {
        val payload = runCatching {
            json.decodeFromString(LocalPackagePayload.serializer(), String(payloadBytes, Charsets.UTF_8))
        }.getOrNull() ?: return null
        val packageId = runCatching { UUID.fromString(payload.packageId) }.getOrNull() ?: return null
        val originTenantId = runCatching { UUID.fromString(payload.tenantId) }.getOrNull() ?: return null
        val expiresAt = runCatching { Instant.parse(payload.expiresAt) }.getOrNull() ?: return null
        return ParsedPackage(
            payload = payload,
            packageId = packageId,
            originTenantId = originTenantId,
            expiresAt = expiresAt,
        )
    }

    private fun resolveImport(
        parsed: ParsedPackage,
        targetTenantId: UUID,
        importedByUserId: UUID,
        dryRun: Boolean,
    ): ImportResult {
        val existing = importedPackageRepository.findById(parsed.packageId)
        if (dryRun) {
            return ImportResult.Preview(
                importedEventIds = existing?.importedEventIds ?: parsed.payload.events.map { UUID.fromString(it.id) },
                originTenantDisplayName = parsed.payload.tenantDisplayName,
                alreadyImported = existing != null,
            )
        }
        if (existing != null) {
            return ImportResult.Success(
                localInstanceId = ensureLocalInstance().id,
                tenantId = targetTenantId,
                importedEventIds = existing.importedEventIds,
                alreadyImported = true,
                originTenantDisplayName = parsed.payload.tenantDisplayName,
            )
        }

        val now = clock.now()
        val importedEventIds = persistEvents(parsed, targetTenantId, importedByUserId, now)
        importedPackageRepository.insert(
            ImportedPackageEntity(
                packageId = parsed.packageId,
                originTenantId = parsed.originTenantId,
                importedEventIds = importedEventIds,
                importedAt = now,
            ),
        )
        return ImportResult.Success(
            localInstanceId = ensureLocalInstance().id,
            tenantId = targetTenantId,
            importedEventIds = importedEventIds,
            alreadyImported = false,
            originTenantDisplayName = parsed.payload.tenantDisplayName,
        )
    }

    private fun persistEvents(
        parsed: ParsedPackage,
        targetTenantId: UUID,
        importedByUserId: UUID,
        now: Instant,
    ): List<UUID> {
        return transaction {
            parsed.payload.events.map { pe ->
                val eventId = UUID.fromString(pe.id)
                eventRepository.insert(buildEvent(pe, eventId, targetTenantId, importedByUserId, parsed, now))
                pe.participants.forEach { pp ->
                    participantRepository.insert(buildParticipant(pp, eventId, now))
                }
                eventId
            }
        }
    }

    private fun buildEvent(
        pe: PackagedEvent,
        eventId: UUID,
        targetTenantId: UUID,
        importedByUserId: UUID,
        parsed: ParsedPackage,
        now: Instant,
    ): EventEntity {
        return EventEntity(
            id = eventId,
            tenantId = targetTenantId,
            name = pe.name,
            description = pe.description,
            status = EventStatus.valueOf(pe.status),
            settings = EventSettings(
                laneType = LaneType.valueOf(pe.laneType),
                measurementType = MeasurementType.valueOf(pe.measurementType),
                maxParticipants = pe.maxParticipants,
                trackLength = pe.trackLength,
            ),
            createdBy = importedByUserId,
            createdAt = now,
            originTenantId = parsed.originTenantId,
            originPackageId = parsed.packageId,
            syncStatus = SyncStatus.IMPORTED,
        )
    }

    private fun buildParticipant(pp: PackagedParticipant, eventId: UUID, now: Instant): ParticipantEntity {
        val participantId = UUID.fromString(pp.id)
        return ParticipantEntity(
            id = participantId,
            eventId = eventId,
            startNumber = pp.startNumber,
            firstName = pp.firstName,
            lastName = pp.lastName,
            club = pp.club,
            status = ParticipantStatus.valueOf(pp.status),
            sortOrder = pp.sortOrder,
            vehicle = pp.vehicleName?.let { name ->
                VehicleEntity(
                    id = UUID.randomUUID(),
                    participantId = participantId,
                    name = name,
                    category = pp.vehicleCategory,
                )
            },
            createdAt = now,
        )
    }

    private class DecodedArtifact(
        val payloadBytes: ByteArray,
        val signatureBytes: ByteArray,
        val publicKey: RSAPublicKey,
    )

    private class ParsedPackage(
        val payload: LocalPackagePayload,
        val packageId: UUID,
        val originTenantId: UUID,
        val expiresAt: Instant,
    )

    private fun ensureLocalInstance(): LocalInstanceEntity {
        return localInstanceRepository.find()
            ?: LocalInstanceEntity(id = UUID.randomUUID(), createdAt = clock.now())
                .also { localInstanceRepository.insert(it) }
    }

    private fun decodeBase64(value: String): ByteArray? {
        return runCatching { Base64.getDecoder().decode(value) }.getOrNull()
    }

    private fun decodePublicKey(base64: String): RSAPublicKey? {
        return runCatching {
            val bytes = Base64.getDecoder().decode(base64)
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes)) as RSAPublicKey
        }.getOrNull()
    }
}
