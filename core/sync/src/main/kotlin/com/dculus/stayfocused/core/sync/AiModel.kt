package com.dculus.stayfocused.core.sync

/** Raw text generation for the AI block builder; the seam that lets tests use a fake instead of Gemini. */
internal interface AiModel {
    /** Returns the model's JSON text for [sentence]. Throws on any failure (network, quota, App Check, safety). */
    suspend fun generate(sentence: String): String
}
