package io.github.raginlundf.racingmanager.application.bootstrap

import java.util.UUID

sealed interface ImportResult {
    data class Success(
        val localInstanceId: UUID,
        val tenantId: UUID,
        val importedEventIds: List<UUID>,
        val alreadyImported: Boolean,
        val originTenantDisplayName: String,
    ) : ImportResult
    data object InvalidArtifact : ImportResult
    data object InvalidSignature : ImportResult
    data object Expired : ImportResult

    /** Not a hard error — `dryRun=true` never mutates state; the caller
        distinguishes this from [Success] purely by the request it made. */
    data class Preview(
        val importedEventIds: List<UUID>,
        val originTenantDisplayName: String,
        val alreadyImported: Boolean,
    ) : ImportResult
}
