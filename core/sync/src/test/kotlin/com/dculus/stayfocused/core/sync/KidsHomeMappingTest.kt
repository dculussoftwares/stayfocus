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
        assertEquals(KidsRule.ALL_DAYS, schedule?.days)
        val weekdays =
            parseRule(
                "w",
                mapOf(
                    "enabled" to true,
                    "type" to "SCHEDULE",
                    "name" to "School",
                    "days" to 31L,
                    "range" to mapOf("start" to "08:00", "end" to "15:00"),
                ),
            )
        assertEquals(31, weekdays?.days)
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

    @Test
    fun blocksMissingTheirTypeFieldsAreLeftOut() {
        val base = mapOf("enabled" to true, "name" to "x")
        assertNull(parseRule("a", base + mapOf("type" to "LIMIT")))
        assertNull(parseRule("b", base + mapOf("type" to "CYCLE", "useMins" to 10)))
        assertNull(parseRule("c", base + mapOf("type" to "SCHEDULE", "range" to mapOf("start" to "08:00"))))
        assertNull(parseRule("d", base + mapOf("type" to "NOW")))
    }

    // FirebaseFunctionsException cannot be built outside the SDK; the function's own refusals are covered by its
    // emulator test (firebase/functions/test/unlinkDevice.test.ts).
    @Test
    fun connectionProblemsAreRetryableAndEverythingElseFails() {
        assertEquals(UnlinkResult.Network, IOException("down").toUnlinkResult())
        assertEquals(UnlinkResult.Network, RuntimeException("x", IOException("no route")).toUnlinkResult())
        assertEquals(UnlinkResult.Failed, IllegalStateException().toUnlinkResult())
    }
}
