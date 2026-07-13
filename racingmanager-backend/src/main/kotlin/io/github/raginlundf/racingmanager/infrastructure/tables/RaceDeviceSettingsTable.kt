package io.github.raginlundf.racingmanager.infrastructure.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.`java`.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

/** At most one row: the local installation's race-device connection settings,
    edited from the UI (device mode, Raspberry Pi WebSocket endpoint, finish
    timeout). Absent until first saved, in which case startup config applies. */
object RaceDeviceSettingsTable : Table("race_device_settings") {
    val id = javaUUID("id")
    val mode = varchar("mode", 32)
    val endpoint = text("endpoint")
    val finishTimeoutMs = long("finish_timeout_ms")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
