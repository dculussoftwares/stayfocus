package com.dculus.stayfocused.core.model

import java.time.DayOfWeek

/** Bitmask of days: bit 0 = Monday ... bit 6 = Sunday. */
@JvmInline
value class DaysOfWeek(
    val mask: Int,
) {
    init {
        require(mask in 0..0x7F) { "mask out of range: $mask" }
    }

    operator fun contains(day: DayOfWeek): Boolean = mask and (1 shl (day.value - 1)) != 0

    val isEmpty: Boolean get() = mask == 0

    companion object {
        val NONE = DaysOfWeek(0)
        val ALL = DaysOfWeek(0b1111111)
        val WEEKDAYS = DaysOfWeek(0b0011111)
        val WEEKENDS = DaysOfWeek(0b1100000)

        fun of(vararg days: DayOfWeek): DaysOfWeek = DaysOfWeek(days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) })
    }
}
