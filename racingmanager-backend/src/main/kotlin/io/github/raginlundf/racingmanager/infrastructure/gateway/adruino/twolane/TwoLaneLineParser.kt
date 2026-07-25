package io.github.raginlundf.racingmanager.infrastructure.gateway.adruino.twolane

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

private const val FIELD_COUNT = 3

/** Parses `<lane>;<state>;<value>` device lines (`.plan/Adruino-impl.md` §2.3).
    Anything unparseable is logged and discarded — never an error, and never guessed
    into a known state — which also covers the truncated first line the board emits
    as a boot artefact. Field widths in the board's flash table are padded to four
    characters (`RUN `, `ARM `), so fields are trimmed. */
object TwoLaneLineParser {

    fun parse(line: String, hostTimestamp: Instant): TwoLaneEvent? {
        val fields = line.trim().split(";")
        if (fields.size != FIELD_COUNT) {
            logger.debug { "Discarding device line with ${fields.size} fields: '$line'" }
            return null
        }
        val lane = TwoLaneLane.from(value = fields[0].trim())
        if (lane == null) {
            logger.debug { "Discarding device line with unknown lane: '$line'" }
            return null
        }
        val value = fields[2].trim().toLongOrNull()
        if (value == null) {
            logger.debug { "Discarding device line with non-numeric value: '$line'" }
            return null
        }
        val state = DeviceState.from(value = fields[1].trim())
        if (state == DeviceState.UNKNOWN) {
            logger.warn { "Unknown device state in '$line' — logged, race state unchanged" }
        }
        return TwoLaneEvent(lane = lane, state = state, value = value, raw = line, hostTimestamp = hostTimestamp)
    }
}
