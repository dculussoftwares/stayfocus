package com.dculus.stayfocused.core.model

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Name and one-line description of a block, as shown in the Block tab (prototype `wizNext`). */
data class BlockNameAndDescription(
    val name: String,
    val description: String,
)

/**
 * The wizard's plain-language summary of a [BlockDraft], ported exactly from the prototype `wizSummary` / `wizNext`.
 * [appLabels] are the display names of the selected apps, in selection order.
 */
object BlockSummary {
    private const val MAX_LISTED_APPS = 2

    /** "Instagram and YouTube" / "Instagram, YouTube +1" / "no apps yet". */
    fun appsText(appLabels: List<String>): String =
        when {
            appLabels.isEmpty() -> {
                "no apps yet"
            }

            appLabels.size > MAX_LISTED_APPS -> {
                "${appLabels.take(MAX_LISTED_APPS).joinToString(", ")} +${appLabels.size - MAX_LISTED_APPS}"
            }

            else -> {
                appLabels.joinToString(" and ")
            }
        }

    /** v5 apps text (V5_SPEC §5): "A and B", "A, B and 2 more", "These apps" when none. */
    fun appsTextV5(appLabels: List<String>): String =
        when {
            appLabels.isEmpty() -> {
                "These apps"
            }

            appLabels.size > MAX_LISTED_APPS -> {
                "${appLabels.take(MAX_LISTED_APPS).joinToString(", ")} and ${appLabels.size - MAX_LISTED_APPS} more"
            }

            else -> {
                appLabels.joinToString(" and ")
            }
        }

    /** v5 `daysTxt`: "every day", "Mon–Fri", "weekends", otherwise "Mon, Tue, …" (and "no days" for none). */
    fun daysTextV5(days: DaysOfWeek): String =
        when {
            days == DaysOfWeek.ALL -> {
                "every day"
            }

            days == DaysOfWeek.WEEKDAYS -> {
                "Mon–Fri"
            }

            days == DaysOfWeek.WEEKENDS -> {
                "weekends"
            }

            days.isEmpty -> {
                "no days"
            }

            else -> {
                DayOfWeek.entries
                    .filter { it in days }
                    .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
            }
        }

    /**
     * The summary card of wizard step 3 (v5, V5_SPEC §5). Same wording as the prototype; an hourly limit (a shipped
     * extra) says "an hour" instead of "a day" and does not lock "until midnight".
     */
    fun sentenceV5(
        draft: BlockDraft,
        appLabels: List<String>,
    ): String {
        val apps = appsTextV5(appLabels)
        val days = daysTextV5(draft.days)
        return when (draft.type) {
            BlockType.LIMIT -> {
                if (draft.period == LimitPeriod.HOURLY) {
                    "$apps can be used ${v5Duration(draft.mins)} an hour, $days. " +
                        "After that they lock until the next hour."
                } else {
                    "$apps can be used ${v5Duration(draft.mins)} a day, $days. After that they lock until midnight."
                }
            }

            BlockType.CYCLE -> {
                "Open $apps for ${draft.use} min, then they lock for ${v5Duration(draft.rest)}. This repeats $days."
            }

            BlockType.SCHEDULE -> {
                "$apps are blocked from ${draft.range.format().replace("–", " to ")}, $days."
            }

            BlockType.NOW -> {
                "$apps are blocked for the next ${v5Duration(draft.now)}."
            }
        }
    }

    fun sentence(
        draft: BlockDraft,
        appLabels: List<String>,
    ): String {
        val apps = appsText(appLabels)
        val days = daysSummary(draft.days)
        return when (draft.type) {
            BlockType.LIMIT -> {
                "Lock $apps after ${durationLabel(draft.mins)} ${periodText(draft.period)}, $days."
            }

            BlockType.CYCLE -> {
                "Each time you open $apps, you get ${durationLabel(draft.use)}. " +
                    "Then it locks for ${durationLabel(draft.rest)}, $days."
            }

            BlockType.SCHEDULE -> {
                "Lock $apps from ${draft.range.format().replace("–", " to ")}, $days."
            }

            BlockType.NOW -> {
                "Lock $apps for the next ${durationLabel(draft.now)}."
            }
        }
    }

    fun nameAndDescription(
        draft: BlockDraft,
        appLabels: List<String>,
    ): BlockNameAndDescription {
        val apps = appLabels.joinToString(", ")
        return when (draft.type) {
            BlockType.LIMIT -> {
                BlockNameAndDescription(
                    "Time limit",
                    "${durationLabel(draft.mins)} ${periodText(draft.period)} · $apps",
                )
            }

            BlockType.CYCLE -> {
                BlockNameAndDescription(
                    "Use, then rest",
                    "${dialLabel(draft.use)} on, ${dialLabel(draft.rest)} off · $apps",
                )
            }

            BlockType.SCHEDULE -> {
                BlockNameAndDescription(
                    "Scheduled block",
                    "${draft.range.format()} · ${daysSummary(draft.days)} · $apps",
                )
            }

            BlockType.NOW -> {
                BlockNameAndDescription("Quick block", "Next ${durationLabel(draft.now)} · $apps")
            }
        }
    }

    private fun periodText(period: LimitPeriod): String = if (period == LimitPeriod.DAILY) "a day" else "an hour"
}
