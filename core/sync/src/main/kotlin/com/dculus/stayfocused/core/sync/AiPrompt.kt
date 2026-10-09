package com.dculus.stayfocused.core.sync

/** Gemini settings for the AI block builder. Only the user's sentence is sent; never installed apps or other data. */
internal object AiPrompt {
    /** Current Gemini Flash model on the Gemini Developer API behind Firebase AI Logic. */
    const val MODEL_NAME = "gemini-3.5-flash"

    const val TIMEOUT_MS = 8_000L

    const val MAX_SENTENCE_LENGTH = 500

    /** Prototype `AI_SYS`, adapted: the schema is enforced by `responseSchema`, so no "reply with only JSON" prose. */
    val SYSTEM_INSTRUCTION: String =
        listOf(
            "You turn a user's sentence into an app-blocking rule for an Android app.",
            "Fields: type, apps (ids), mins, period, use, rest, range (\"HH:MM–HH:MM\", 24h, en dash), now, " +
                "days (only the days the block applies; mon, tue, wed, thu, fri, sat, sun), " +
                "note (one short sentence restating the rule).",
            "type meanings: limit = allowance per day/hour then block (mins, period). " +
                "cycle = each time opened, allow \"use\" minutes then block for \"rest\" minutes. " +
                "schedule = block between times (range). now = block immediately for \"now\" minutes.",
            "Known app ids: instagram, youtube, reddit, whatsapp, chrome, x, spotify, maps. " +
                "\"social media\" = instagram, reddit, x, youtube. School days / weekdays = mon–fri. " +
                "Weekends = sat, sun. Default to all 7 days if no days are mentioned.",
        ).joinToString("\n")
}
