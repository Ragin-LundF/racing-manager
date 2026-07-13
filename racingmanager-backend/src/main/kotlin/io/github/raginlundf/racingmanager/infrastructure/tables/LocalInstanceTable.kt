package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

/** At most one row: this local installation's stable identity, generated
    once on its first package import (design §H/§I). */
object LocalInstanceTable : Table("local_instance") {
    val id = javaUUID("id")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
