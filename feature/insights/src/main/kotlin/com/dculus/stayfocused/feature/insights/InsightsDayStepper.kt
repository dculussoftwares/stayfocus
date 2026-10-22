package com.dculus.stayfocused.feature.insights

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** How far back the stepper goes: today (0) to -6. */
internal const val OLDEST_DAY_OFFSET = -6

/** Title of the day stepper; the weekday name is locale-formatted, the other two are string resources. */
internal sealed interface DayTitle {
    data object Today : DayTitle

    data object Yesterday : DayTitle

    data class Weekday(
        val name: String,
    ) : DayTitle
}

internal fun dayTitle(
    offset: Int,
    date: LocalDate,
    locale: Locale = Locale.getDefault(),
): DayTitle =
    when (offset) {
        0 -> DayTitle.Today
        -1 -> DayTitle.Yesterday
        else -> DayTitle.Weekday(date.dayOfWeek.getDisplayName(TextStyle.FULL, locale))
    }

/** "06 OCT". */
internal fun dayDateLabel(
    date: LocalDate,
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter.ofPattern("dd MMM", locale).format(date).uppercase(locale)

internal fun canStepBack(offset: Int): Boolean = offset > OLDEST_DAY_OFFSET

internal fun canStepForward(offset: Int): Boolean = offset < 0
