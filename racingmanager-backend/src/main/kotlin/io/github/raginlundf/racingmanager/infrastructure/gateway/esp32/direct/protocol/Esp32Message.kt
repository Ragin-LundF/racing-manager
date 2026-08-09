package io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire messages exchanged with an ESP32 timing module over the direct
    WebSocket connection (`docs/track_setup/en/PROTOCOL.md`). Unlike the
    HARDWARE mode's command/event split, this protocol is one flat, bidirectional
    message set — a device and the server both send and receive from this same
    hierarchy. Serialized polymorphically with a `type` discriminator holding the
    dotted names from the spec (`"sensor.event"`, `"race.arm"`, …); JSON field
    names are snake_case, matching the ArduinoJson-authored firmware payloads
    (see [Esp32MessageCodec]). `role`/`event`/`transport` stay raw strings at this
    wire layer — the gateway parses them into internal enums, the same split
    `TwoLaneLineParser` uses for the Arduino line protocol. */
@Serializable
sealed interface Esp32Message {
    val v: Int

    /** Device → server, once per connection: identifies which module this socket is. */
    @Serializable
    @SerialName("device.register")
    data class DeviceRegister(
        val deviceId: String,
        val bootId: String,
        val role: String,
        val firmware: String,
        val capabilities: List<String> = emptyList(),
        override val v: Int = ESP32_PROTOCOL_VERSION,
    ) : Esp32Message

    /** Device → server, every second: liveness + raw sensor state for the status UI. */
    @Serializable
    @SerialName("device.heartbeat")
    data class DeviceHeartbeat(
        val deviceId: String,
        val bootId: String,
        val uptimeMs: Long,
        val transport: String,
        val sensors: Map<String, String> = emptyMap(),
        override val v: Int = ESP32_PROTOCOL_VERSION,
    ) : Esp32Message

    /** Server → device: acknowledges an accepted [SensorEvent] by its [messageId]. */
    @Serializable
    @SerialName("event.ack")
    data class EventAck(
        val messageId: String,
        override val v: Int = ESP32_PROTOCOL_VERSION,
    ) : Esp32Message

    /** Server → device: prepare [lanes] for [raceId]. Only sent when the race-control
        handshake is enabled (`Esp32WebSocketDirectSettings.useRaceControlHandshake`). */
    @Serializable
    @SerialName("race.arm")
    data class RaceArm(
        val raceId: String,
        val lanes: List<Int>,
        val syncEpochUs: Long? = null,
        override val v: Int = ESP32_PROTOCOL_VERSION,
    ) : Esp32Message

    /** Device → server: reply to [RaceArm] once its sensors are ready. */
    @Serializable
    @SerialName("race.armed")
    data class RaceArmed(
        val raceId: String,
        val deviceId: String,
        val sensorsReady: Boolean,
        override val v: Int = ESP32_PROTOCOL_VERSION,
    ) : Esp32Message

    /** Server → device: release the race. */
    @Serializable
    @SerialName("race.start")
    data class RaceStart(
        val raceId: String,
        val startReferenceUs: Long,
        override val v: Int = ESP32_PROTOCOL_VERSION,
    ) : Esp32Message

    /** Server → device: abandon [raceId]; return to idle. */
    @Serializable
    @SerialName("race.reset")
    data class RaceReset(
        val raceId: String,
        override val v: Int = ESP32_PROTOCOL_VERSION,
    ) : Esp32Message

    /** Device → server: a light barrier fired. [raceId]/[syncTimestampUs]/[syncUncertaintyUs]
        are only populated when the race-control handshake and time-sync are enabled;
        the concrete deployment (start-beam to finish-beam timing) leaves them null and the
        gateway uses its own receipt time instead. */
    @Serializable
    @SerialName("sensor.event")
    data class SensorEvent(
        val messageId: String,
        val deviceId: String,
        val bootId: String,
        val sequence: Long,
        val role: String,
        val lane: Int,
        val event: String,
        val localTimestampUs: Long,
        val raceId: String? = null,
        val syncTimestampUs: Long? = null,
        val syncUncertaintyUs: Long? = null,
        override val v: Int = ESP32_PROTOCOL_VERSION,
    ) : Esp32Message

    /** Server → device: one round of the time-sync exchange. */
    @Serializable
    @SerialName("time.sync_request")
    data class TimeSyncRequest(
        val nonce: String,
        val serverSendUs: Long,
        override val v: Int = ESP32_PROTOCOL_VERSION,
    ) : Esp32Message

    /** Device → server: reply to [TimeSyncRequest]. */
    @Serializable
    @SerialName("time.sync_response")
    data class TimeSyncResponse(
        val nonce: String,
        val deviceReceiveUs: Long,
        val deviceSendUs: Long,
        override val v: Int = ESP32_PROTOCOL_VERSION,
    ) : Esp32Message

    /** Server → device: reply to an undecodable or unknown-`type` frame. */
    @Serializable
    @SerialName("error.unsupported")
    data class ErrorUnsupported(
        val message: String,
        val unsupportedType: String? = null,
        override val v: Int = ESP32_PROTOCOL_VERSION,
    ) : Esp32Message
}
