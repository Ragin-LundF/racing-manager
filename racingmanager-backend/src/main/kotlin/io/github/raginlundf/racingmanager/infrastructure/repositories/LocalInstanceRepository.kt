package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.domain.bootstrap.LocalInstanceEntity
import io.github.raginlundf.racingmanager.infrastructure.tables.LocalInstanceTable
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class LocalInstanceRepository {

    fun find(): LocalInstanceEntity? = transaction {
        LocalInstanceTable.selectAll().singleOrNull()?.let {
            LocalInstanceEntity(id = it[LocalInstanceTable.id], createdAt = it[LocalInstanceTable.createdAt])
        }
    }

    fun insert(instance: LocalInstanceEntity) = transaction {
        LocalInstanceTable.insert {
            it[id] = instance.id
            it[createdAt] = instance.createdAt
        }
    }
}
