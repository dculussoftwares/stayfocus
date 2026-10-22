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

    private val three = listOf("Instagram", "YouTube", "Reddit")

    @Test
    fun v5AppsText() {
        assertEquals("These apps", BlockSummary.appsTextV5(emptyList()))
        assertEquals("Instagram", BlockSummary.appsTextV5(ig))
        assertEquals("Instagram and YouTube", BlockSummary.appsTextV5(two))
        assertEquals("Instagram, YouTube and 1 more", BlockSummary.appsTextV5(three))
        assertEquals("Instagram, YouTube and 2 more", BlockSummary.appsTextV5(four))
    }

    @Test
    fun v5DaysText() {
        assertEquals("every day", BlockSummary.daysTextV5(DaysOfWeek.ALL))
        assertEquals("Mon–Fri", BlockSummary.daysTextV5(DaysOfWeek.WEEKDAYS))
        assertEquals("weekends", BlockSummary.daysTextV5(DaysOfWeek.WEEKENDS))
        assertEquals("no days", BlockSummary.daysTextV5(DaysOfWeek.NONE))
        assertEquals("Mon, Wed", BlockSummary.daysTextV5(DaysOfWeek.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
    }

    @Test
    fun v5Sentences() {
        val all = DaysOfWeek.ALL
        assertEquals(
            "Instagram and YouTube can be used 45 min a day, every day. After that they lock until midnight.",
            BlockSummary.sentenceV5(draft(BlockType.LIMIT) { copy(mins = 45, days = all) }, two),
        )
        assertEquals(
            "Instagram can be used 1h 30m a day, Mon–Fri. After that they lock until midnight.",
            BlockSummary.sentenceV5(draft(BlockType.LIMIT) { copy(mins = 90) }, ig),
        )
        assertEquals(
            "Open Instagram, YouTube and 1 more for 10 min, then they lock for 30 min. This repeats weekends.",
            BlockSummary.sentenceV5(
                draft(BlockType.CYCLE) { copy(use = 10, rest = 30, days = DaysOfWeek.WEEKENDS) },
                three,
            ),
        )
        assertEquals(
            "Instagram and YouTube are blocked from 22:00 to 07:00, Mon, Tue.",
            BlockSummary.sentenceV5(
                draft(BlockType.SCHEDULE) {
                    copy(
                        range = TimeRange.parse("22:00–07:00"),
                        days = DaysOfWeek.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                    )
                },
                two,
            ),
        )
        assertEquals(
            "These apps are blocked for the next 1h.",
            BlockSummary.sentenceV5(draft(BlockType.NOW) { copy(now = 60) }, emptyList()),
        )
    }
}
