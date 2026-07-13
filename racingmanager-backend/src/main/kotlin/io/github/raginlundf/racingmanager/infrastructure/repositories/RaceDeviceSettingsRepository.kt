package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceMode
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceSettings
import io.github.raginlundf.racingmanager.infrastructure.tables.RaceDeviceSettingsTable
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.time.Clock

/** Persists the single-row race-device settings edited from the UI. Absent until
    first saved — callers fall back to startup configuration in that case. */
class RaceDeviceSettingsRepository {

    fun find(): RaceDeviceSettings? {
        return transaction {
            RaceDeviceSettingsTable.selectAll().singleOrNull()?.let {
                RaceDeviceSettings(
                    mode = RaceDeviceMode.from(value = it[RaceDeviceSettingsTable.mode]),
                    endpoint = it[RaceDeviceSettingsTable.endpoint],
                    finishTimeoutMs = it[RaceDeviceSettingsTable.finishTimeoutMs],
                )
            }
        }
    }

    /** Replaces the single settings row. Delete-then-insert keeps the "at most one
        row" invariant without depending on the previous row's id. */
    fun save(settings: RaceDeviceSettings) {
        transaction {
            RaceDeviceSettingsTable.deleteAll()
            RaceDeviceSettingsTable.insert {
                it[id] = UUID.randomUUID()
                it[mode] = settings.mode.name
                it[endpoint] = settings.endpoint
                it[finishTimeoutMs] = settings.finishTimeoutMs
                it[updatedAt] = Clock.System.now()
            }
        }
    }
}
