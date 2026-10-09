package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.model.AiBlockResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manual / nightly eval, not part of the per-PR run: sends every cloud fixture sentence to the live Gemini model
 * (same model, system instruction and response schema as the app, via the Gemini Developer API) and prints accuracy.
 *
 * Run: `GEMINI_API_KEY=... ./gradlew :core:sync:testDebugUnitTest --tests '*GeminiEvalTest*' -i`
 */
class GeminiEvalTest {
    @Serializable
    private data class Case(
        val sentence: String,
        val expected: AiBlockResult,
        val localOnly: Boolean = false,
    )

    @Serializable
    private data class Fixture(
        val cases: List<Case>,
    )

    @Test
    fun `live model accuracy on the fixture`() {
        val key = System.getenv("GEMINI_API_KEY")
        assumeTrue("GEMINI_API_KEY not set; skipping the live eval", !key.isNullOrBlank())
        val fixture =
            Json { ignoreUnknownKeys = true }.decodeFromString(
                Fixture.serializer(),
                File("../../fixtures/ai/block-sentences.json").readText(),
            )
        val cases = fixture.cases.filterNot { it.localOnly }
        var correct = 0
        cases.forEach { case ->
            val got = runCatching { AiBlockResult.parse(call(key!!, case.sentence)) }.getOrNull()
            val problems = got?.let { diff(it, case.expected) } ?: listOf("no usable response")
            if (problems.isEmpty()) {
                correct++
            } else {
                println(
                    "EVAL MISS \"${case.sentence}\": ${problems.joinToString("; ")}",
                )
            }
        }
        println(
            "EVAL ACCURACY $correct/${cases.size} (${"%.1f".format(
                100.0 * correct / cases.size,
            )}%) model=${AiPrompt.MODEL_NAME}",
        )
    }

    private fun diff(
        got: AiBlockResult,
        want: AiBlockResult,
    ): List<String> =
        buildList {
            if (got.type != want.type) add("type ${got.type} != ${want.type}")
            if (got.apps.orEmpty().toSet() != want.apps.orEmpty().toSet()) add("apps ${got.apps} != ${want.apps}")
            when (want.type) {
                "limit" -> {
                    if (got.mins != want.mins) add("mins ${got.mins} != ${want.mins}")
                    if (got.period != want.period) add("period ${got.period} != ${want.period}")
                }

                "cycle" -> {
                    if (got.use != want.use) add("use ${got.use} != ${want.use}")
                    if (got.rest != want.rest) add("rest ${got.rest} != ${want.rest}")
                }

                "schedule" -> {
                    if (got.range != want.range) add("range ${got.range} != ${want.range}")
                }

                "now" -> {
                    if (got.now != want.now) add("now ${got.now} != ${want.now}")
                }
            }
            // The app maps an absent or empty days list to all seven days.
            val gotDays =
                got.days
                    .orEmpty()
                    .ifEmpty { null }
                    ?.toSet()
            if (gotDays != want.days?.toSet()) add("days ${got.days} != ${want.days}")
        }

    private fun call(
        key: String,
        sentence: String,
    ): String {
        val body =
            buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { add(buildJsonObject { put("text", AiPrompt.SYSTEM_INSTRUCTION) }) }
                }
                putJsonArray("contents") {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            putJsonArray("parts") { add(buildJsonObject { put("text", sentence) }) }
                        },
                    )
                }
                putJsonObject("generationConfig") {
                    put("responseMimeType", "application/json")
                    put("responseSchema", schema())
                }
            }
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${AiPrompt.MODEL_NAME}:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val text = conn.inputStream.bufferedReader().readText()
            return Json
                .parseToJsonElement(text)
                .jsonObject["candidates"]!!
                .jsonArray[0]
                .jsonObject["content"]!!
                .jsonObject["parts"]!!
                .jsonArray[0]
                .jsonObject["text"]!!
                .jsonPrimitive.contentOrNull
                .orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    private fun schema(): JsonObject {
        fun prim(type: String) = buildJsonObject { put("type", type) }

        fun enum(vararg values: String) =
            buildJsonObject {
                put("type", "STRING")
                put("format", "enum")
                put("enum", JsonArray(values.map(::JsonPrimitive)))
            }

        fun array(items: JsonObject) =
            buildJsonObject {
                put("type", "ARRAY")
                put("items", items)
            }
        return buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                put("type", enum("limit", "cycle", "schedule", "now"))
                put("apps", array(prim("STRING")))
                put("mins", prim("NUMBER"))
                put("period", enum("daily", "hourly"))
                put("use", prim("NUMBER"))
                put("rest", prim("NUMBER"))
                put("range", prim("STRING"))
                put("now", prim("NUMBER"))
                put("days", array(enum("mon", "tue", "wed", "thu", "fri", "sat", "sun")))
                put("note", prim("STRING"))
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
    }
}
