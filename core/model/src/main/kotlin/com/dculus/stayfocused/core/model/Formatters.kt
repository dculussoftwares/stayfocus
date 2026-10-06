package com.dculus.stayfocused.core.model

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** "2h 27m" / "41m". */
fun formatMinutes(minutes: Double): String {
    val m = Math.round(minutes).toInt()
    val h = m / 60
    val r = m % 60
    return if (h > 0) "${h}h ${r}m" else "${r}m"
}

fun formatMinutes(minutes: Int): String = formatMinutes(minutes.toDouble())

private fun two(v: Long) = v.toString().padStart(2, '0')

/** Countdown "m:ss", rounding up to the next second; negative clamps to 0. */
fun mmss(millis: Long): String {
    val s = maxOf(0L, ceil(millis / 1000.0).toLong())
    return "${s / 60}:${two(s % 60)}"
}

/** "hh:mm:ss", rounding down; negative clamps to 0. */
fun hms(millis: Long): String {
    val s = maxOf(0L, floor(millis / 1000.0).toLong())
    return "${two(s / 3600)}:${two((s / 60) % 60)}:${two(s % 60)}"
}

/** Dial/preset label: "15m" / "1h" / "1h30". */
fun dialLabel(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 != 0 -> "${minutes / 60}h${minutes % 60}"
    else -> "${minutes / 60}h"
}

/** Sentence label: "45 min" / "1 h" / "1 h 30 min". */
fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 != 0 -> "${minutes / 60} h ${minutes % 60} min"
    else -> "${minutes / 60} h"
}

/** "every day" / "on weekdays" / "on Mon, Wed" / "no days". */
fun daysSummary(days: DaysOfWeek): String = when {
    days == DaysOfWeek.ALL -> "every day"
    days == DaysOfWeek.WEEKDAYS -> "on weekdays"
    days.isEmpty -> "no days"
    else -> "on " + DayOfWeek.entries.filter { it in days }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
}
