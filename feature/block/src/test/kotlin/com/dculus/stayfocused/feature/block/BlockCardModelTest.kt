package com.dculus.stayfocused.feature.block

import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.LimitPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

class BlockCardModelTest {
    private val usage = mapOf(pkg("reddit") to 20, pkg("x") to 15, pkg("instagram") to 40)

    @Test
    fun dailyLimitCountsAllAppsOfTheBlock() {
        val p = blockProgress(limitBlock(), usage)
        assertEquals(BlockProgress(35, 30), p)
        assertEquals(35f / 30f, p!!.fraction, 0.0001f)
    }

    @Test
    fun hourlyLimitHasNoMeter() {
        assertNull(blockProgress(limitBlock().copy(period = LimitPeriod.HOURLY), usage))
    }

    @Test
    fun cycleCapsUsedAtTheOpenWindow() {
        assertEquals(BlockProgress(10, 10), blockProgress(cycleBlock(), usage))
        assertEquals(BlockProgress(0, 10), blockProgress(cycleBlock(), emptyMap()))
        assertEquals(BlockProgress(6, 10), blockProgress(cycleBlock(), mapOf(pkg("youtube") to 6)))
    }

    @Test
    fun scheduleAndNowHaveNoMeter() {
        assertNull(blockProgress(scheduleBlock(), usage))
        assertNull(blockProgress(limitBlock().copy(type = BlockType.NOW), usage))
    }

    @Test
    fun nowBlockEndsAtStartPlusDuration() {
        val now =
            limitBlock().copy(
                type = BlockType.NOW,
                durationMins = 90,
                startedAt = Instant.parse("2026-03-01T21:00:30Z"),
            )
        assertEquals(LocalTime.of(22, 30), nowBlockEnds(now, ZoneOffset.UTC))
        assertEquals(LocalTime.of(3, 30), nowBlockEnds(now, ZoneOffset.ofHours(5)))
        assertNull(nowBlockEnds(now.copy(startedAt = null), ZoneOffset.UTC))
    }

    @Test
    fun daysStyles() {
        assertEquals(DaysStyle.EVERY_DAY, daysStyle(DaysOfWeek.ALL))
        assertEquals(DaysStyle.WEEKDAYS, daysStyle(DaysOfWeek.WEEKDAYS))
        assertEquals(DaysStyle.WEEKENDS, daysStyle(DaysOfWeek.WEEKENDS))
        assertEquals(DaysStyle.LIST, daysStyle(DaysOfWeek(0b0000101)))
    }

    @Test
    fun titleJoinsTwoAppsThenCountsTheRest() {
        assertEquals("Instagram", blockTitle(listOf("Instagram")))
        assertEquals("Instagram + YouTube", blockTitle(listOf("Instagram", "YouTube")))
        assertEquals("Instagram + YouTube +2", blockTitle(listOf("Instagram", "YouTube", "Reddit", "X")))
    }
}
