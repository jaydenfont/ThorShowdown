package com.example.thorshowdown.showdown

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** JSON payloads embedded in protocol lines. Unknown keys are ignored so server additions don't break parsing. */
internal object ProtocolJson {
    val json: Json = Json { ignoreUnknownKeys = true }

    /** Decodes [text] with [strategy], or returns null if it isn't valid for that shape. */
    fun <T> decodeOrNull(
        strategy: DeserializationStrategy<T>,
        text: String,
    ): T? =
        try {
            json.decodeFromString(strategy, text)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    /** Payload of `|updatesearch|`. */
    @Serializable
    data class UpdateSearchPayload(
        val searching: List<String> = emptyList(),
        val games: Map<String, String>? = null,
    )
}
