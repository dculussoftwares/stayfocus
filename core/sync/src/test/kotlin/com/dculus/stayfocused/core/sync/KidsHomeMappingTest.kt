package com.dculus.stayfocused.core.sync

import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KidsHomeMappingTest {
    @Test
    fun aDailyLimitBecomesARule() {
        val rule =
            parseRule(
                "b1",
                mapOf(
                    "enabled" to true,
                    "type" to "LIMIT",
                    "name" to "Instagram",
                    "limitMins" to 30L,
                    "period" to "DAILY",
                ),
            )
        assertEquals(KidsRule("b1", "Instagram", KidsRuleType.Limit, limitMins = 30, hourly = false), rule)
    }

    @Test
    fun scheduleCycleAndNowKeepTheirFields() {
        val schedule =
            parseRule(
                "s",
                mapOf(
                    "enabled" to true,
                    "type" to "SCHEDULE",
                    "name" to "Games",
                    "range" to mapOf("start" to "08:00", "end" to "15:00"),
                ),
            )
        assertEquals("08:00", schedule?.start)
        assertEquals("15:00", schedule?.end)
        val cycle =
            parseRule(
                "c",
                mapOf(
                    "enabled" to true,
                    "type" to "CYCLE",
                    "name" to "Study",
                    "useMins" to 25,
                    "restMins" to 5,
                ),
            )
        assertEquals(25 to 5, cycle?.useMins to cycle?.restMins)
        val now = parseRule("n", mapOf("enabled" to true, "type" to "NOW", "name" to "Focus", "durationMins" to 50))
        assertEquals(50, now?.durationMins)
    }

    @Test
    fun switchedOffOrUnreadableBlocksAreLeftOut() {
        assertNull(parseRule("a", mapOf("enabled" to false, "type" to "LIMIT", "name" to "x")))
        assertNull(parseRule("b", mapOf("enabled" to true, "type" to "WEIRD", "name" to "x")))
        assertNull(parseRule("c", mapOf("enabled" to true, "type" to "LIMIT", "name" to "")))
        assertNull(parseRule("d", mapOf("type" to "LIMIT", "name" to "x")))
    }

    // FirebaseFunctionsException cannot be built outside the SDK, so the permission-denied case (a device that is gone)
    // is covered by the emulator test of the function and the manual two-phone check.
    @Test
    fun connectionProblemsAreRetryableAndEverythingElseFails() {
        assertEquals(UnlinkResult.Network, IOException("down").toUnlinkResult())
        assertEquals(UnlinkResult.Network, RuntimeException("x", IOException("no route")).toUnlinkResult())
        assertEquals(UnlinkResult.Failed, IllegalStateException().toUnlinkResult())
    }
}
