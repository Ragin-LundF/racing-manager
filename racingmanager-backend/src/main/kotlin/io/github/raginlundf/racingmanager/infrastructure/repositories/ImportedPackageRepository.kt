package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.bootstrap.ImportedPackageEntity
import io.github.raginlundf.racingmanager.infrastructure.tables.ImportedPackageTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class ImportedPackageRepository {

    fun findById(packageId: UUID): ImportedPackageEntity? {
        return transaction {
            ImportedPackageTable.selectAll().where { ImportedPackageTable.packageId eq packageId }
                .singleOrNull()
                ?.let {
                    ImportedPackageEntity(
                        packageId = it[ImportedPackageTable.packageId],
                        originTenantId = it[ImportedPackageTable.originTenantId],
                        importedEventIds = it[ImportedPackageTable.importedEventIds]
                            .split(",").filter { s -> s.isNotBlank() }
                            .map(transform = UUID::fromString),
                        importedAt = it[ImportedPackageTable.importedAt],
                    )
                }
        }
    }

    fun insert(entry: ImportedPackageEntity) {
        transaction {
            ImportedPackageTable.insert {
                it[packageId] = entry.packageId
                it[originTenantId] = entry.originTenantId
                it[importedEventIds] = entry.importedEventIds.joinToString(separator = ",")
                it[importedAt] = entry.importedAt
            }
        }
    }
}
