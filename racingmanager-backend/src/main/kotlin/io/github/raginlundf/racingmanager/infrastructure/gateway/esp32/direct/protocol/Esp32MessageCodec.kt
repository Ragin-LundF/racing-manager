package io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/** Encodes/decodes [Esp32Message] frames. Both ends use the same field names —
    snake_case, per the ArduinoJson-authored firmware payloads in `PROTOCOL.md` —
    so [JsonNamingStrategy.SnakeCase] converts the idiomatic camelCase Kotlin
    properties automatically instead of an `@SerialName` per field. */
@OptIn(ExperimentalSerializationApi::class)
object Esp32MessageCodec {
    private val json = Json {
        classDiscriminator = "type"
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(message: Esp32Message): String {
        return json.encodeToString(serializer = Esp32Message.serializer(), value = message)
    }

    /** Decodes one frame, or null when it is undecodable — bad JSON, an unknown
        `type`, or an unsupported [ESP32_PROTOCOL_VERSION]. `PROTOCOL.md` requires
        answering such a frame with `error.unsupported`; sending that reply is the
        caller's job once it knows decoding failed. */
    fun decodeOrNull(text: String): Esp32Message? {
        return runCatching { json.decodeFromString(deserializer = Esp32Message.serializer(), string = text) }
            .getOrNull()
            ?.takeIf { it.v == ESP32_PROTOCOL_VERSION }
    }
}
