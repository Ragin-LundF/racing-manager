package io.github.raginlundf.racingmanager.application.sync

import java.util.UUID

sealed interface SyncResultsResult {
    data class Success(val syncedResultId: UUID) : SyncResultsResult
    data object InstanceNotFound : SyncResultsResult
    data object InstanceRevoked : SyncResultsResult
    data object EventNotFound : SyncResultsResult
    data object EventNotLocked : SyncResultsResult
}
