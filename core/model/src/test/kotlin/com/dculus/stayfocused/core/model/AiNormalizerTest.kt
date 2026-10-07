package com.dculus.stayfocused.core.model

import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiNormalizerTest {
    private val ig = KnownApps.packages.getValue("instagram")
    private val yt = KnownApps.packages.getValue("youtube")
    private val reddit = KnownApps.packages.getValue("reddit")
    private val installed =
        listOf(
            AppInfo(ig, "Instagram"),
            AppInfo(yt, "YouTube"),
            AppInfo(reddit, "Reddit"),
            AppInfo("com.supercell.clashofclans", "Clash of Clans"),
        )

    private fun norm(
        r: AiBlockResult,
        sentence: String = "",
        apps: List<AppInfo> = installed,
    ) = AiNormalizer.normalize(r, sentence, apps)

    @Test
    fun `type maps known values and defaults to limit`() {
        assertEquals(BlockType.LIMIT, norm(AiBlockResult(type = "limit")).type)
        assertEquals(BlockType.CYCLE, norm(AiBlockResult(type = "cycle")).type)
        assertEquals(BlockType.SCHEDULE, norm(AiBlockResult(type = "schedule")).type)
        assertEquals(BlockType.NOW, norm(AiBlockResult(type = "now")).type)
        assertEquals(BlockType.LIMIT, norm(AiBlockResult(type = "bogus")).type)
        assertEquals(BlockType.LIMIT, norm(AiBlockResult(type = null)).type)
    }

    @Test
    fun `numbers are rounded, defaulted, floored at 1 and clamped to the dial max`() {
        assertEquals(45, norm(AiBlockResult(mins = 44.6)).mins)
        assertEquals(30, norm(AiBlockResult(mins = null)).mins)
        assertEquals(30, norm(AiBlockResult(mins = 0.0)).mins)
        assertEquals(30, norm(AiBlockResult(mins = Double.NaN)).mins)
        assertEquals(1, norm(AiBlockResult(mins = 0.2)).mins)
        assertEquals(1, norm(AiBlockResult(mins = -5.0)).mins)
        assertEquals(180, norm(AiBlockResult(mins = 999.0)).mins)
        assertEquals(10, norm(AiBlockResult(use = null)).use)
        assertEquals(60, norm(AiBlockResult(use = 90.0)).use)
        assertEquals(30, norm(AiBlockResult(rest = null)).rest)
        assertEquals(180, norm(AiBlockResult(rest = 500.0)).rest)
        assertEquals(60, norm(AiBlockResult(now = null)).now)
        assertEquals(480, norm(AiBlockResult(now = 10_000.0)).now)
        assertEquals(480, norm(AiBlockResult(now = Double.POSITIVE_INFINITY)).now)
    }

    @Test
    fun `period is hourly only for hourly`() {
        assertEquals(LimitPeriod.HOURLY, norm(AiBlockResult(period = "hourly")).period)
        assertEquals(LimitPeriod.DAILY, norm(AiBlockResult(period = "daily")).period)
        assertEquals(LimitPeriod.DAILY, norm(AiBlockResult(period = "weekly")).period)
        assertEquals(LimitPeriod.DAILY, norm(AiBlockResult(period = null)).period)
    }

    @Test
    fun `range accepts well-formed values and falls back otherwise`() {
        val default = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))
        assertEquals(
            TimeRange(LocalTime.of(22, 0), LocalTime.of(7, 0)),
            norm(AiBlockResult(range = "22:00–07:00")).range,
        )
        assertEquals(default, norm(AiBlockResult(range = null)).range)
        assertEquals(default, norm(AiBlockResult(range = "22:00-07:00")).range)
        assertEquals(default, norm(AiBlockResult(range = "9:00–17:00")).range)
        assertEquals(default, norm(AiBlockResult(range = "25:00–26:00")).range)
        assertEquals(default, norm(AiBlockResult(range = "09:00–17:00\n")).range)
        assertEquals(default, norm(AiBlockResult(range = "12:00–12:00")).range)
    }

    @Test
    fun `weekday and weekend keywords in the sentence override the model days`() {
        val r = AiBlockResult(days = listOf("sat"))
        assertEquals(DaysOfWeek.WEEKDAYS, norm(r, "block it on school days").days)
        assertEquals(DaysOfWeek.WEEKDAYS, norm(r, "Weekdays only").days)
        assertEquals(DaysOfWeek.WEEKDAYS, norm(r, "on workdays").days)
        assertEquals(DaysOfWeek.WEEKDAYS, norm(r, "on work days").days)
        assertEquals(DaysOfWeek.WEEKENDS, norm(AiBlockResult(), "every weekend").days)
    }

    @Test
    fun `model days are matched on the first three letters`() {
        val days = norm(AiBlockResult(days = listOf("Monday", "wed", "FRI", "xyz")), "block it").days
        assertEquals(DaysOfWeek.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), days)
    }

    @Test
    fun `no usable days means all seven`() {
        assertEquals(DaysOfWeek.ALL, norm(AiBlockResult(days = null)).days)
        assertEquals(DaysOfWeek.ALL, norm(AiBlockResult(days = emptyList())).days)
        assertEquals(DaysOfWeek.ALL, norm(AiBlockResult(days = listOf("someday", ""))).days)
    }

    @Test
    fun `app names map to installed packages by known id or label`() {
        val d = norm(AiBlockResult(apps = listOf(" Instagram ", "clash of clans", "reddit", "unknown", "")))
        assertEquals(setOf(ig, "com.supercell.clashofclans", reddit), d.apps)
        assertEquals(listOf(ig, "com.supercell.clashofclans", reddit), d.apps.toList())
    }

    @Test
    fun `known id that is not installed is dropped`() {
        val d = norm(AiBlockResult(apps = listOf("instagram", "youtube")), apps = listOf(AppInfo(yt, "YouTube")))
        assertEquals(setOf(yt), d.apps)
    }

    @Test
    fun `no resolvable apps falls back to instagram and youtube`() {
        assertEquals(setOf(ig, yt), norm(AiBlockResult(apps = listOf("nothing"))).apps)
        assertEquals(setOf(ig, yt), norm(AiBlockResult(apps = null)).apps)
        assertEquals(setOf(yt), norm(AiBlockResult(), apps = listOf(AppInfo(yt, "YouTube"))).apps)
        assertEquals(emptySet(), norm(AiBlockResult(), apps = emptyList()).apps)
    }

    @Test
    fun `draft is marked from AI with the note and target`() {
        val target = BlockTarget.Device("d1")
        val d = AiNormalizer.normalize(AiBlockResult(note = "30 min a day."), "x", installed, target)
        assertTrue(d.fromAi)
        assertEquals("30 min a day.", d.aiNote)
        assertEquals(target, d.target)
        assertEquals("", norm(AiBlockResult(note = null)).aiNote)
    }

    @Test
    fun `parse extracts the JSON object from surrounding prose and ignores unknown keys`() {
        val out =
            "Sure! ```json\n" +
                """{"type":"cycle","apps":["instagram"],"use":10,"rest":30.0,"days":["mon"],"note":"ok","extra":1}""" +
                "\n```"
        val r = AiBlockResult.parse(out)
        assertEquals("cycle", r?.type)
        assertEquals(listOf("instagram"), r?.apps)
        assertEquals(10.0, r?.use)
        assertEquals(30.0, r?.rest)
        assertEquals("ok", r?.note)
    }

    @Test
    fun `parse returns null for missing or invalid JSON`() {
        assertNull(AiBlockResult.parse("no json here"))
        assertNull(AiBlockResult.parse("} {"))
        assertNull(AiBlockResult.parse("{not json}"))
        assertNull(AiBlockResult.parse("""{"mins":[1,2]}"""))
    }

    @Test
    fun `non-AI draft defaults match the prototype`() {
        val d = BlockDraft()
        assertFalse(d.fromAi)
        assertEquals(DaysOfWeek.WEEKDAYS, d.days)
        assertEquals(setOf(ig, yt), d.apps)
    }

    @Test
    fun `a weekend group keeps explicitly named extra days`() {
        val days = AiNormalizer.toDays(listOf("sat", "sun", "mon"), "Block Instagram on weekends and Monday")
        assertEquals(DaysOfWeek.of(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), days)
        assertEquals(DaysOfWeek.WEEKENDS, AiNormalizer.toDays(listOf("mon"), "Block Instagram on weekends"))
    }
}
