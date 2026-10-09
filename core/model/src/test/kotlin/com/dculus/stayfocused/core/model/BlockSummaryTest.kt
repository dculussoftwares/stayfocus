package com.dculus.stayfocused.core.model

import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockSummaryTest {
    private val ig = listOf("Instagram")
    private val two = listOf("Instagram", "YouTube")
    private val four = listOf("Instagram", "YouTube", "Reddit", "X")

    private fun draft(
        type: BlockType,
        change: BlockDraft.() -> BlockDraft = { this },
    ) = BlockDraft(type = type).change()

    private fun sentence(
        d: BlockDraft,
        labels: List<String> = two,
    ) = BlockSummary.sentence(d, labels)

    @Test
    fun limitPerDayDefault() =
        assertEquals(
            "Lock Instagram and YouTube after 30 min a day, on weekdays.",
            sentence(draft(BlockType.LIMIT)),
        )

    @Test
    fun limitPerHour() =
        assertEquals(
            "Lock Instagram after 15 min an hour, every day.",
            sentence(
                draft(BlockType.LIMIT) { copy(period = LimitPeriod.HOURLY, mins = 15, days = DaysOfWeek.ALL) },
                ig,
            ),
        )

    @Test
    fun limitWithHoursAndMinutes() =
        assertEquals(
            "Lock Instagram after 1 h 30 min a day, on weekdays.",
            sentence(draft(BlockType.LIMIT) { copy(mins = 90) }, ig),
        )

    @Test
    fun limitWithWholeHour() =
        assertEquals(
            "Lock Instagram after 1 h a day, on weekdays.",
            sentence(draft(BlockType.LIMIT) { copy(mins = 60) }, ig),
        )

    @Test
    fun threeAppsAreTruncatedToTwoPlusOne() =
        assertEquals(
            "Lock Instagram, YouTube +1 after 30 min a day, on weekdays.",
            sentence(draft(BlockType.LIMIT), listOf("Instagram", "YouTube", "Reddit")),
        )

    @Test
    fun fourAppsAreTruncatedToTwoPlusTwo() =
        assertEquals(
            "Lock Instagram, YouTube +2 after 30 min a day, on weekdays.",
            sentence(draft(BlockType.LIMIT), four),
        )

    @Test
    fun noAppsYet() =
        assertEquals(
            "Lock no apps yet after 30 min a day, on weekdays.",
            sentence(draft(BlockType.LIMIT), emptyList()),
        )

    @Test
    fun cycleDefault() =
        assertEquals(
            "Each time you open Instagram and YouTube, you get 10 min. Then it locks for 30 min, on weekdays.",
            sentence(draft(BlockType.CYCLE)),
        )

    @Test
    fun cycleWithHourRest() =
        assertEquals(
            "Each time you open Instagram, you get 5 min. Then it locks for 2 h, every day.",
            sentence(draft(BlockType.CYCLE) { copy(use = 5, rest = 120, days = DaysOfWeek.ALL) }, ig),
        )

    @Test
    fun scheduleDefault() =
        assertEquals(
            "Lock Instagram and YouTube from 09:00 to 17:00, on weekdays.",
            sentence(draft(BlockType.SCHEDULE)),
        )

    @Test
    fun scheduleOvernightWithSomeDays() =
        assertEquals(
            "Lock Instagram from 22:00 to 07:00, on Mon, Wed.",
            sentence(
                draft(BlockType.SCHEDULE) {
                    copy(
                        range = TimeRange.parse("22:00–07:00"),
                        days = DaysOfWeek.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                    )
                },
                ig,
            ),
        )

    @Test
    fun scheduleWithNoDays() =
        assertEquals(
            "Lock Instagram from 09:00 to 17:00, no days.",
            sentence(draft(BlockType.SCHEDULE) { copy(days = DaysOfWeek.NONE) }, ig),
        )

    @Test
    fun weekendsAreListedByName() =
        assertEquals(
            "Lock Instagram from 09:00 to 17:00, on Sat, Sun.",
            sentence(draft(BlockType.SCHEDULE) { copy(days = DaysOfWeek.WEEKENDS) }, ig),
        )

    @Test
    fun nowIgnoresDays() =
        assertEquals(
            "Lock Instagram and YouTube for the next 1 h.",
            sentence(draft(BlockType.NOW)),
        )

    @Test
    fun nowInMinutes() =
        assertEquals(
            "Lock Instagram for the next 45 min.",
            sentence(draft(BlockType.NOW) { copy(now = 45) }, ig),
        )

    @Test
    fun limitNameAndDescription() =
        assertEquals(
            BlockNameAndDescription("Time limit", "30 min a day · Instagram, YouTube"),
            BlockSummary.nameAndDescription(draft(BlockType.LIMIT), two),
        )

    @Test
    fun cycleNameAndDescription() =
        assertEquals(
            BlockNameAndDescription("Use, then rest", "10m on, 1h30 off · Instagram, YouTube"),
            BlockSummary.nameAndDescription(draft(BlockType.CYCLE) { copy(rest = 90) }, two),
        )

    @Test
    fun scheduleNameAndDescription() =
        assertEquals(
            BlockNameAndDescription("Scheduled block", "09:00–17:00 · on weekdays · Instagram, YouTube"),
            BlockSummary.nameAndDescription(draft(BlockType.SCHEDULE), two),
        )

    @Test
    fun nowNameAndDescription() =
        assertEquals(
            BlockNameAndDescription("Quick block", "Next 1 h · Instagram, YouTube, Reddit, X"),
            BlockSummary.nameAndDescription(draft(BlockType.NOW), four),
        )
}
