package com.dculus.stayfocused.core.model

import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeRangeTest {
    private fun t(s: String) = LocalTime.parse(s)

    @Test
    fun normalRange() {
        val r = TimeRange.parse("09:00–17:00")
        assertFalse(r.crossesMidnight)
        assertTrue(t("09:00") in r)
        assertTrue(t("12:00") in r)
        assertTrue(t("16:59") in r)
        assertFalse(t("17:00") in r)
        assertFalse(t("08:59") in r)
    }

    @Test
    fun overnightRange() {
        val r = TimeRange.parse("22:00–07:00")
        assertTrue(r.crossesMidnight)
        assertTrue(t("22:00") in r)
        assertTrue(t("23:59") in r)
        assertTrue(t("00:00") in r)
        assertTrue(t("06:59") in r)
        assertFalse(t("07:00") in r)
        assertFalse(t("12:00") in r)
        assertFalse(t("21:59") in r)
    }

    @Test
    fun emptyRangeContainsNothing() {
        assertFalse(t("09:00") in TimeRange(t("09:00"), t("09:00")))
    }

    @Test
    fun parseAndFormatRoundTrip() {
        assertEquals("09:00–17:00", TimeRange.parse("09:00–17:00").format())
        assertEquals("22:05–07:30", TimeRange(t("22:05"), t("07:30")).format())
        assertFailsWith<IllegalArgumentException> { TimeRange.parse("09:00-17:00") }
        assertFailsWith<IllegalArgumentException> { TimeRange.parse("09:00:30–17:00") }
        assertFailsWith<IllegalArgumentException> { TimeRange(t("09:00:30"), t("17:00")) }
    }

    @Test
    fun formatIsLocaleIndependent() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-SA-u-nu-arab"))
            val r = TimeRange(t("09:05"), t("17:30"))
            assertEquals("09:05–17:30", r.format())
            assertEquals(r, TimeRange.parse(r.format()))
        } finally {
            java.util.Locale.setDefault(original)
        }
    }

    @Test
    fun daysOfWeek() {
        assertTrue(DayOfWeek.SUNDAY in DaysOfWeek.ALL)
        assertTrue(DayOfWeek.MONDAY in DaysOfWeek.WEEKDAYS)
        assertFalse(DayOfWeek.SATURDAY in DaysOfWeek.WEEKDAYS)
        assertTrue(DayOfWeek.SATURDAY in DaysOfWeek.WEEKENDS)
        assertFalse(DayOfWeek.FRIDAY in DaysOfWeek.WEEKENDS)
    }

    @Test
    fun knownAppsAndDials() {
        assertEquals(8, KnownApps.packages.size)
        assertEquals(4, KnownApps.socialMedia.size)
        assertTrue("com.reddit.frontpage" in KnownApps.socialMedia)
        assertEquals(480, DialConfigs.BlockNow.max)
        assertEquals(listOf(5, 10, 15, 30), DialConfigs.CycleUse.presets)
    }
}
