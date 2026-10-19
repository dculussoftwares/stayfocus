package com.dculus.stayfocused.core.sync

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChildStatusFieldsTest {
    private val status = ChildStatus(battery = 80, charging = false, currentApp = "Chat", online = true)

    @Test
    fun statusCarriesTheAndroidVersionWhenKnown() {
        val fields = statusFields(status.copy(osVersion = "15"))
        assertEquals("15", fields["osVersion"])
        assertEquals(80, fields["battery"])
    }

    @Test
    fun statusLeavesOutAMissingOrBlankVersionAndCapsALongOne() {
        assertFalse("osVersion" in statusFields(status))
        assertFalse("osVersion" in statusFields(status.copy(osVersion = " ")))
        assertEquals(50, (statusFields(status.copy(osVersion = "9".repeat(80)))["osVersion"] as String).length)
    }

    private fun summary(hourly: List<Int>?) =
        UsageSummary(
            date = "2026-03-04",
            totalMins = 90,
            apps = listOf(UsageAppEntry("a.b", "Alpha", 90, 3)),
            hourly = hourly,
        )

    @Test
    fun usageCarriesTwentyFourHourlyBucketsNextToTheTotal() {
        val fields = usageFields(summary(List(24) { it }))
        assertEquals(90, fields["totalMins"])
        assertEquals(List(24) { it }, fields["hourly"])
    }

    @Test
    fun usageClampsBucketsToAnHour() {
        val fields = usageFields(summary(List(24) { if (it == 0) 75 else -3 }))
        assertEquals(List(24) { if (it == 0) 60 else 0 }, fields["hourly"])
    }

    @Test
    fun usageWithoutExactly24BucketsSendsNoHourlyField() {
        assertNull(usageFields(summary(null))["hourly"])
        assertFalse("hourly" in usageFields(summary(List(23) { 1 })))
        assertFalse("hourly" in usageFields(summary(List(25) { 1 })))
        assertTrue("apps" in usageFields(summary(null)))
    }
}
