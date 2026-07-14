package io.github.raginlundf.racingmanager.infrastructure.gateway.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Clock

/** Encodes/decodes protocol v1 frames via serializable envelope objects. Both ends
use this codec: the adapter encodes commands and decodes events, the device does
the reverse. The polymorphic payload carries its own `type` discriminator. */
object MessageCodec {
    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeCommand(raceId: String?, command: DeviceCommand): String {
        val envelope = DeviceCommandEnvelope(
            messageId = newMessageId(),
            timestamp = now(),
            payload = command,
            raceId = raceId,
        )
        return json.encodeToString(serializer = DeviceCommandEnvelope.serializer(), value = envelope)
    }

    fun encodeEvent(raceId: String?, event: DeviceEvent): String {
        val envelope = DeviceEventEnvelope(
            messageId = newMessageId(),
            timestamp = now(),
            payload = event,
            raceId = raceId,
        )
        return json.encodeToString(serializer = DeviceEventEnvelope.serializer(), value = envelope)
    }

    fun decodeCommand(text: String): DecodedCommand {
        val envelope = decode(text = text, serializer = DeviceCommandEnvelope.serializer(), kind = "command")
        requireSupportedVersion(version = envelope.protocolVersion, raceId = envelope.raceId)
        return DecodedCommand(messageId = envelope.messageId, raceId = envelope.raceId, command = envelope.payload)
    }

    fun decodeEvent(text: String): DecodedEvent {
        val envelope = decode(text = text, serializer = DeviceEventEnvelope.serializer(), kind = "event")
        requireSupportedVersion(version = envelope.protocolVersion, raceId = envelope.raceId)
        return DecodedEvent(messageId = envelope.messageId, raceId = envelope.raceId, event = envelope.payload)
    }

    private fun <T> decode(text: String, serializer: KSerializer<T>, kind: String): T {
        return runCatching { json.decodeFromString(deserializer = serializer, string = text) }
            .getOrElse { failure ->
                val raceId = runCatching {
                    json.decodeFromString(
                        deserializer = EnvelopeMeta.serializer(),
                        string = text
                    ).raceId
                }.getOrNull()
                throw DeviceProtocolException(raceId = raceId, message = "Undecodable $kind: ${failure.message}")
            }
    }

    private fun requireSupportedVersion(version: Int, raceId: String?) {
        if (version != PROTOCOL_VERSION) {
            throw DeviceProtocolException(raceId = raceId, message = "Unsupported protocolVersion $version")
        }
    }

    private fun newMessageId(): String {
        return UUID.randomUUID().toString()
    }

    private fun now(): String {
        return Clock.System.now().toString()
    }
}
