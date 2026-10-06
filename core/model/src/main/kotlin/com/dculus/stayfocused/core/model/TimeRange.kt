package com.dculus.stayfocused.core.model

import java.time.LocalTime

/** A daily time window. [start] is inclusive, [end] exclusive; may cross midnight. */
data class TimeRange(val start: LocalTime, val end: LocalTime) {
    val crossesMidnight: Boolean get() = end < start

    operator fun contains(time: LocalTime): Boolean = when {
        start == end -> false
        crossesMidnight -> time >= start || time < end
        else -> time >= start && time < end
    }

    /** "09:00–17:00" (en dash). */
    fun format(): String = "${start.hhmm()}$SEPARATOR${end.hhmm()}"

    companion object {
        private const val SEPARATOR = "–"

        fun parse(text: String): TimeRange {
            val parts = text.split(SEPARATOR)
            require(parts.size == 2) { "Expected HH:mm${SEPARATOR}HH:mm, got: $text" }
            return TimeRange(LocalTime.parse(parts[0].trim()), LocalTime.parse(parts[1].trim()))
        }
    }
}

private fun LocalTime.hhmm(): String = "%02d:%02d".format(hour, minute)
