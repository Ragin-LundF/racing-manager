package io.github.raginlundf.racingmanager.api.bootstrap.models

import kotlinx.serialization.Serializable

@Serializable
data class LocalPackageImportResponseModel(
    val localInstanceId: String,
    val tenantId: String,
    val importedEventIds: List<String>,
    val alreadyImported: Boolean,
    val dryRun: Boolean,
    val originTenantDisplayName: String,
)
