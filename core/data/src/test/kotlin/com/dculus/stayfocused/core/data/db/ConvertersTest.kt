package com.dculus.stayfocused.core.data.db

import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.LimitPeriod
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConvertersTest {
    private val c = Converters()

    @Test
    fun `instant round trips through epoch millis and null stays null`() {
        val i = Instant.ofEpochMilli(1_717_000_000_123)
        assertEquals(i, c.longToInstant(c.instantToLong(i)))
        assertNull(c.instantToLong(null))
        assertNull(c.longToInstant(null))
    }

    @Test
    fun `date and time round trip`() {
        val d = LocalDate.of(2026, 2, 28)
        assertEquals(d, c.longToLocalDate(c.localDateToLong(d)))
        assertNull(c.localDateToLong(null))
        assertNull(c.longToLocalDate(null))
        val t = LocalTime.of(7, 5)
        assertEquals("07:05", c.localTimeToString(t))
        assertEquals(t, c.stringToLocalTime("07:05"))
        assertNull(c.localTimeToString(null))
        assertNull(c.stringToLocalTime(null))
    }

    @Test
    fun `days and enums round trip`() {
        assertEquals(DaysOfWeek.WEEKENDS, c.intToDays(c.daysToInt(DaysOfWeek.WEEKENDS)))
        BlockType.entries.forEach { assertEquals(it, c.stringToBlockType(c.blockTypeToString(it))) }
        BlockSource.entries.forEach { assertEquals(it, c.stringToBlockSource(c.blockSourceToString(it))) }
        LimitPeriod.entries.forEach { assertEquals(it, c.stringToLimitPeriod(c.limitPeriodToString(it))) }
        assertNull(c.limitPeriodToString(null))
        assertNull(c.stringToLimitPeriod(null))
    }
}
