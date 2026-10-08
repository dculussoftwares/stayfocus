package com.dculus.stayfocused.feature.block

import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.KnownApps
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockTemplateTest {
    private val allKnown = KnownApps.packages.values.toSet()

    private fun pkgs(vararg ids: String) = ids.map { KnownApps.packages.getValue(it) }.toSet()

    @Test
    fun mindfulScrollingIsTenOnThirtyOffEveryDay() {
        val p = BlockTemplate.MINDFUL_SCROLLING.prefill(BlockTarget.ThisPhone, allKnown)
        assertEquals(BlockType.CYCLE, p.draft.type)
        assertEquals(10, p.draft.use)
        assertEquals(30, p.draft.rest)
        assertEquals(DaysOfWeek.ALL, p.draft.days)
        assertEquals(pkgs("instagram", "youtube", "reddit"), p.draft.apps)
        assertEquals(WIZARD_STEP_RULES, p.startStep)
    }

    @Test
    fun studyTimeIsSixteenToTwentyOnSchoolDays() {
        val p = BlockTemplate.STUDY_TIME.prefill(BlockTarget.ThisPhone, allKnown)
        assertEquals(BlockType.SCHEDULE, p.draft.type)
        assertEquals(TimeRange.parse("16:00–20:00"), p.draft.range)
        assertEquals(DaysOfWeek.WEEKDAYS, p.draft.days)
        assertEquals(pkgs("instagram", "youtube", "reddit"), p.draft.apps)
    }

    @Test
    fun socialLimitIsThirtyMinutesADay() {
        val p = BlockTemplate.SOCIAL_LIMIT.prefill(BlockTarget.ThisPhone, allKnown)
        assertEquals(BlockType.LIMIT, p.draft.type)
        assertEquals(30, p.draft.mins)
        assertEquals(LimitPeriod.DAILY, p.draft.period)
        assertEquals(DaysOfWeek.ALL, p.draft.days)
        assertEquals(pkgs("instagram", "reddit", "x"), p.draft.apps)
    }

    @Test
    fun bedtimeCoversEveryKnownAppOvernight() {
        val p = BlockTemplate.BEDTIME.prefill(BlockTarget.ThisPhone, allKnown)
        assertEquals(TimeRange.parse("22:00–07:00"), p.draft.range)
        assertEquals(DaysOfWeek.ALL, p.draft.days)
        assertEquals(allKnown, p.draft.apps)
    }

    @Test
    fun onlyInstalledAppsArePrefilled() {
        val p = BlockTemplate.MINDFUL_SCROLLING.prefill(BlockTarget.ThisPhone, pkgs("youtube", "chrome"))
        assertEquals(pkgs("youtube"), p.draft.apps)
        assertEquals(WIZARD_STEP_RULES, p.startStep)
    }

    @Test
    fun noInstalledTemplateAppOpensAtTheAppPicker() {
        val p = BlockTemplate.SOCIAL_LIMIT.prefill(BlockTarget.ThisPhone, pkgs("chrome", "maps"))
        assertEquals(emptySet<String>(), p.draft.apps)
        assertEquals(WIZARD_STEP_APPS, p.startStep)
        assertEquals(BlockType.LIMIT, p.draft.type)
    }

    @Test
    fun prefillKeepsTheTarget() {
        val device = BlockTarget.Device("d1")
        assertEquals(
            device,
            BlockTemplate.BEDTIME
                .prefill(device, allKnown)
                .draft.target,
        )
    }

    @Test
    fun idsRoundTrip() {
        BlockTemplate.entries.forEach { assertEquals(it, BlockTemplate.fromId(it.id)) }
        assertEquals(null, BlockTemplate.fromId("nope"))
        assertEquals(null, BlockTemplate.fromId(null))
    }
}
