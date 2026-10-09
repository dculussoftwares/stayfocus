package com.dculus.stayfocused.core.model

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
