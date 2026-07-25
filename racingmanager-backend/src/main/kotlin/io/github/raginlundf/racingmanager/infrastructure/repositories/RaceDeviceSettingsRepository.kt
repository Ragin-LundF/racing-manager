package io.github.raginlundf.racingmanager.infrastructure.repositories

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceMode
import io.github.raginlundf.racingmanager.infrastructure.gateway.RaceDeviceSettings
import io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane.ArduinoTwoLaneSettings
import io.github.raginlundf.racingmanager.infrastructure.tables.RaceDeviceSettingsTable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

private val json = Json { ignoreUnknownKeys = true }

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
                    arduino = it[RaceDeviceSettingsTable.arduinoOptions]?.let(::decodeArduino),
                )
            }
        }
    }

    /** A stored block written by an older/newer shape must not stop the server from
        booting — fall back to the spec defaults and log it. */
    private fun decodeArduino(stored: String): ArduinoTwoLaneSettings? {
        return runCatching { json.decodeFromString<ArduinoTwoLaneSettings>(stored) }
            .onFailure { logger.warn(throwable = it) { "Ignoring unreadable stored Arduino options" } }
            .getOrNull()
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
                it[arduinoOptions] = settings.arduino?.let { options -> json.encodeToString(options) }
                it[updatedAt] = Clock.System.now()
            }
        }
    }
}
