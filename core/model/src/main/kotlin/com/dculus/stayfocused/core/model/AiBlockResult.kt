package com.dculus.stayfocused.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The JSON object the model returns (prototype `AI_SYS` schema). Every field is optional and untrusted;
 * [AiNormalizer] turns it into a valid [BlockDraft].
 *
 * `apps` holds app names from the fixed [KnownApps] id list, never the user's installed apps (privacy).
 */
@Serializable
data class AiBlockResult(
    val type: String? = null,
    val apps: List<String>? = null,
    val mins: Double? = null,
    val period: String? = null,
    val use: Double? = null,
    val rest: Double? = null,
    val range: String? = null,
    val now: Double? = null,
    val days: List<String>? = null,
    val note: String? = null,
) {
    companion object {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }

        /**
         * Parses the first-`{` to last-`}` span of the model output (like the prototype's `match(/\{[\s\S]*\}/)`),
         * or returns null if there is none or it is not valid JSON.
         */
        fun parse(output: String): AiBlockResult? {
            val start = output.indexOf('{')
            val end = output.lastIndexOf('}')
            if (start < 0 || end < start) return null
            return try {
                json.decodeFromString(serializer(), output.substring(start, end + 1))
            } catch (
                @Suppress("SwallowedException") e: SerializationException,
            ) {
                null
            } catch (
                @Suppress("SwallowedException") e: IllegalArgumentException,
            ) {
                null
            }
        }
    }
}
