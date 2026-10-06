package com.dculus.stayfocused.core.model

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** A daily time window. [start] is inclusive, [end] exclusive; may cross midnight. */
data class TimeRange(val start: LocalTime, val end: LocalTime) {
    init {
        require(start.second == 0 && start.nano == 0 && end.second == 0 && end.nano == 0) {
            "TimeRange has minute precision: $start, $end"
        }
    }

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
        internal val HHMM: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

        fun parse(text: String): TimeRange {
            val parts = text.split(SEPARATOR)
            require(parts.size == 2) { "Expected HH:mm${SEPARATOR}HH:mm, got: $text" }
            return try {
                TimeRange(LocalTime.parse(parts[0].trim(), HHMM), LocalTime.parse(parts[1].trim(), HHMM))
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("Expected HH:mm${SEPARATOR}HH:mm, got: $text", e)
            }
        }
    }
}

private fun LocalTime.hhmm(): String = TimeRange.HHMM.format(this)
