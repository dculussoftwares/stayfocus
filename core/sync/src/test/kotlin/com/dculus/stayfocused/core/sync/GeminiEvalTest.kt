package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.model.AiBlockResult
import com.dculus.stayfocused.core.model.AiNormalizer
import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.KnownApps
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private val installed =
        KnownApps.packages.map { (id, pkg) -> AppInfo(pkg, id.replaceFirstChar { it.uppercase() }) }

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
            // Request failures (bad key, quota, outage) must fail the eval, not count as model misses.
            val got = AiBlockResult.parse(call(key!!, case.sentence))
            val problems = got?.let { diff(case.sentence, it, case.expected) } ?: listOf("no usable response")
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

    @Test
    fun `effective rules apply the app's normalisation defaults`() {
        fun same(
            sentence: String,
            a: AiBlockResult,
            b: AiBlockResult,
        ) = diff(sentence, a, b).isEmpty()
        val all = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        // Omitted days = all seven days; sentence keywords override the model's days.
        assertTrue(same("Instagram 30 min", AiBlockResult(type = "limit", days = all), AiBlockResult(type = "limit")))
        assertTrue(same("on school days", AiBlockResult(type = "limit", days = all), AiBlockResult(type = "limit")))
        val monday = AiBlockResult(type = "limit", days = listOf("mon"))
        assertFalse(same("Instagram 30 min", monday, AiBlockResult(type = "limit")))
        // Rounding and defaults.
        assertTrue(same("x", AiBlockResult(type = "limit", mins = 44.6), AiBlockResult(type = "limit", mins = 45.0)))
        assertTrue(same("x", AiBlockResult(type = "schedule"), AiBlockResult(type = "schedule", range = "09:00–17:00")))
        assertTrue(same("x", AiBlockResult(type = "now"), AiBlockResult(type = "now", now = 60.0)))
    }

    /** What the app would build (`AiNormalizer`) from a result, so defaults, rounding and clamping match. */
    private fun comparable(
        r: AiBlockResult,
        sentence: String,
    ): Map<String, Any?> {
        val d = AiNormalizer.normalize(r, sentence, installed)
        return buildMap {
            put("type", d.type)
            put("apps", d.apps)
            put("days", d.days)
            when (d.type) {
                BlockType.LIMIT -> {
                    put("mins", d.mins)
                    put("period", d.period)
                }

                BlockType.CYCLE -> {
                    put("use", d.use)
                    put("rest", d.rest)
                }

                BlockType.SCHEDULE -> {
                    put("range", d.range)
                }

                BlockType.NOW -> {
                    put("now", d.now)
                }
            }
        }
    }

    private fun diff(
        sentence: String,
        got: AiBlockResult,
        want: AiBlockResult,
    ): List<String> {
        val g = comparable(got, sentence)
        val w = comparable(want, sentence)
        return (w.keys + g.keys).filter { g[it] != w[it] }.map { "$it ${g[it]} != ${w[it]}" }
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
            if (conn.responseCode !in HTTP_OK_RANGE) {
                val details =
                    conn.errorStream
                        ?.bufferedReader()
                        ?.readText()
                        .orEmpty()
                        .take(MAX_ERROR_CHARS)
                error("Gemini request failed: HTTP ${conn.responseCode} $details")
            }
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
            putJsonArray("required") { add(JsonPrimitive("type")) }
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
        const val MAX_ERROR_CHARS = 500
        val HTTP_OK_RANGE = 200..299
    }
}
