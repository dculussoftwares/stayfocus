package com.dculus.vesper.core.blocking.screen

import org.junit.Test
import kotlin.test.assertEquals

class AppTimerChipFormatTest {
    @Test
    fun `formats minutes and seconds, rounding up`() {
        assertEquals("12:34", formatTimeLeft(12 * 60_000L + 34_000L))
        assertEquals("0:01", formatTimeLeft(1L))
        assertEquals("0:00", formatTimeLeft(0L))
        assertEquals("0:00", formatTimeLeft(-5_000L))
    }

    @Test
    fun `shows hours from an hour up`() {
        assertEquals("1:02:03", formatTimeLeft(3_723_000L))
        assertEquals("1:00:00", formatTimeLeft(3_600_000L))
        assertEquals("59:59", formatTimeLeft(3_599_000L))
    }
}
