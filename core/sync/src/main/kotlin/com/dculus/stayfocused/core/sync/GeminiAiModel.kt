package com.dculus.stayfocused.core.sync

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig

/** Gemini through Firebase AI Logic (Gemini Developer API), constrained to the `AiBlockResult` JSON shape. */
internal class GeminiAiModel : AiModel {
    private val model: GenerativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = AiPrompt.MODEL_NAME,
            generationConfig =
                generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = SCHEMA
                },
            systemInstruction = content { text(AiPrompt.SYSTEM_INSTRUCTION) },
        )
    }

    override suspend fun generate(sentence: String): String =
        checkNotNull(model.generateContent(sentence).text) { "Empty model response" }

    private companion object {
        val SCHEMA: Schema =
            Schema.obj(
                mapOf(
                    "type" to Schema.enumeration(listOf("limit", "cycle", "schedule", "now")),
                    "apps" to Schema.array(Schema.string()),
                    "mins" to Schema.double(),
                    "period" to Schema.enumeration(listOf("daily", "hourly")),
                    "use" to Schema.double(),
                    "rest" to Schema.double(),
                    "range" to Schema.string(),
                    "now" to Schema.double(),
                    "days" to Schema.array(Schema.enumeration(listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun"))),
                    "note" to Schema.string(),
                ),
                optionalProperties = listOf("apps", "mins", "period", "use", "rest", "range", "now", "days", "note"),
            )
    }
}
