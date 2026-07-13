package io.github.raginlundf.racingmanager.domain.bootstrap

import kotlin.time.Instant
import java.util.UUID

data class ImportedPackageEntity(
    val packageId: UUID,
    val originTenantId: UUID,
    val importedEventIds: List<UUID>,
    val importedAt: Instant,
)
