package com.dculus.stayfocused.core.data.repository

import org.junit.Test
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.assertEquals

class SystemZoneClockTest {
    @Test fun followsTheDefaultZoneAtEveryCall() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            assertEquals(ZoneId.of("Asia/Kolkata"), SystemZoneClock.zone)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(ZoneId.of("America/New_York"), SystemZoneClock.zone)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
