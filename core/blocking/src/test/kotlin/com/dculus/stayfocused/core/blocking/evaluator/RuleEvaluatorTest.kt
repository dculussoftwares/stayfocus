package com.dculus.stayfocused.core.blocking.evaluator

import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.BreakSession
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.FocusSession
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.LockedApp
import com.dculus.stayfocused.core.model.TemporaryAllowance
import com.dculus.stayfocused.core.model.TimeRange
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals

private const val APP = "com.example.app"
private const val OTHER = "com.example.other"
private const val MIN = 60_000L

private val UTC = ZoneId.of("UTC")
private val BERLIN = ZoneId.of("Europe/Berlin")

private fun at(
    text: String,
    zone: ZoneId = UTC,
): ZonedDateTime = LocalDateTime.parse(text).atZone(zone)

private fun inst(
    text: String,
    zone: ZoneId = UTC,
): Instant = at(text, zone).toInstant()

private fun range(text: String) = TimeRange.parse(text)

private fun block(
    id: String = "b1",
    type: BlockType,
    apps: Set<String> = setOf(APP),
    limitMins: Int? = null,
    period: LimitPeriod? = null,
    useMins: Int? = null,
    restMins: Int? = null,
    range: TimeRange? = null,
    durationMins: Int? = null,
    startedAt: Instant? = null,
    days: DaysOfWeek = DaysOfWeek.ALL,
    enabled: Boolean = true,
) = Block(
    id = id,
    target = BlockTarget.ThisPhone,
    type = type,
    name = id,
    apps = apps,
    limitMins = limitMins,
    period = period,
    useMins = useMins,
    restMins = restMins,
    range = range,
    durationMins = durationMins,
    startedAt = startedAt,
    days = days,
    enabled = enabled,
    createdAt = Instant.EPOCH,
    source = BlockSource.MANUAL,
)

private fun ctx(
    now: ZonedDateTime,
    vararg blocks: Block,
    pkg: String = APP,
    lockedApps: List<LockedApp> = emptyList(),
    breakSession: BreakSession? = null,
    focusSession: FocusSession? = null,
    allowances: List<TemporaryAllowance> = emptyList(),
    usage: Map<String, AppUsageSnapshot> = emptyMap(),
    cycleStates: Map<CycleKey, CycleState> = emptyMap(),
    allowlist: Set<String> = emptySet(),
) = EvaluationContext(
    now = now,
    pkg = pkg,
    blocks = blocks.toList(),
    lockedApps = lockedApps,
    breakSession = breakSession,
    focusSession = focusSession,
    allowances = allowances,
    usage = usage,
    cycleStates = cycleStates,
    allowlist = allowlist,
)

private fun blocked(
    reason: BlockReason,
    blockId: String?,
    until: Instant?,
) = Decision.Block(reason, blockId, until)

private fun breakUntil(end: Instant) = BreakSession(end.minusSeconds(600), end, 10)

private fun focusUntil(end: Instant) = FocusSession(end.minusSeconds(600), end, 10)

private fun lock(pkg: String = APP) = LockedApp(pkg, BlockTarget.ThisPhone, Instant.EPOCH)

class RuleEvaluatorTest {
    private val wed = at("2026-10-07T12:30:00") // Wednesday

    // ---- basics, allowlist, allowances ----

    @Test
    fun `no blocks allows`() = assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed)))

    @Test
    fun `block on another app does not apply`() {
        val b =
            block(type = BlockType.NOW, apps = setOf(OTHER), durationMins = 60, startedAt = inst("2026-10-07T12:00:00"))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, b)))
    }

    @Test
    fun `allowlist beats everything`() {
        val c =
            ctx(
                wed,
                block(type = BlockType.NOW, durationMins = 60, startedAt = inst("2026-10-07T12:00:00")),
                lockedApps = listOf(lock()),
                breakSession = breakUntil(inst("2026-10-07T13:00:00")),
                focusSession = focusUntil(inst("2026-10-07T13:00:00")),
                allowlist = setOf(APP),
            )
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(c))
    }

    @Test
    fun `valid allowance overrides a block`() {
        val b = block(type = BlockType.NOW, durationMins = 60, startedAt = inst("2026-10-07T12:00:00"))
        val c = ctx(wed, b, allowances = listOf(TemporaryAllowance(APP, inst("2026-10-07T12:45:00"))))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(c))
    }

    @Test
    fun `allowance overrides a daily limit`() {
        val b = block(type = BlockType.LIMIT, limitMins = 30, period = LimitPeriod.DAILY)
        val c =
            ctx(
                wed,
                b,
                usage = mapOf(APP to AppUsageSnapshot(31 * MIN, 0)),
                allowances = listOf(TemporaryAllowance(APP, inst("2026-10-07T13:00:00"))),
            )
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(c))
    }

    @Test
    fun `expired allowance does not override`() {
        val b = block(type = BlockType.NOW, durationMins = 60, startedAt = inst("2026-10-07T12:00:00"))
        val c = ctx(wed, b, allowances = listOf(TemporaryAllowance(APP, wed.toInstant())))
        assertEquals(blocked(BlockReason.NOW, "b1", inst("2026-10-07T13:00:00")), RuleEvaluator.evaluate(c))
    }

    @Test
    fun `allowance for another app does not override`() {
        val b = block(type = BlockType.NOW, durationMins = 60, startedAt = inst("2026-10-07T12:00:00"))
        val c = ctx(wed, b, allowances = listOf(TemporaryAllowance(OTHER, inst("2026-10-07T18:00:00"))))
        assertEquals(blocked(BlockReason.NOW, "b1", inst("2026-10-07T13:00:00")), RuleEvaluator.evaluate(c))
    }

    // ---- break / focus / manual lock ----

    @Test
    fun `break blocks every app until it ends`() {
        val end = inst("2026-10-07T12:40:00")
        assertEquals(
            blocked(BlockReason.BREAK, null, end),
            RuleEvaluator.evaluate(ctx(wed, breakSession = breakUntil(end))),
        )
    }

    @Test
    fun `finished break is ignored`() {
        val c = ctx(wed, breakSession = breakUntil(wed.toInstant()))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(c))
    }

    @Test
    fun `focus blocks every app until it ends`() {
        val end = inst("2026-10-07T13:00:00")
        assertEquals(
            blocked(BlockReason.FOCUS, null, end),
            RuleEvaluator.evaluate(ctx(wed, focusSession = focusUntil(end))),
        )
    }

    @Test
    fun `finished focus is ignored`() {
        val c = ctx(wed, focusSession = focusUntil(inst("2026-10-07T12:00:00")))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(c))
    }

    @Test
    fun `manual lock is indefinite`() {
        assertEquals(
            blocked(BlockReason.MANUAL_LOCK, null, null),
            RuleEvaluator.evaluate(ctx(wed, lockedApps = listOf(lock()))),
        )
    }

    @Test
    fun `manual lock of another app is ignored`() {
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, lockedApps = listOf(lock(OTHER)))))
    }

    // ---- NOW ----

    @Test
    fun `now block is active inside its window`() {
        val b = block(type = BlockType.NOW, durationMins = 30, startedAt = inst("2026-10-07T12:15:00"))
        assertEquals(blocked(BlockReason.NOW, "b1", inst("2026-10-07T12:45:00")), RuleEvaluator.evaluate(ctx(wed, b)))
    }

    @Test
    fun `now block starting exactly now is active`() {
        val b = block(type = BlockType.NOW, durationMins = 30, startedAt = wed.toInstant())
        assertEquals(blocked(BlockReason.NOW, "b1", inst("2026-10-07T13:00:00")), RuleEvaluator.evaluate(ctx(wed, b)))
    }

    @Test
    fun `now block ending exactly now is over`() {
        val b = block(type = BlockType.NOW, durationMins = 30, startedAt = inst("2026-10-07T12:00:00"))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, b)))
    }

    @Test
    fun `now block in the future is not active`() {
        val b = block(type = BlockType.NOW, durationMins = 30, startedAt = inst("2026-10-07T12:31:00"))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, b)))
    }

    @Test
    fun `now block ignores its days`() {
        val b =
            block(
                type = BlockType.NOW,
                durationMins = 60,
                startedAt = inst("2026-10-07T12:00:00"),
                days = DaysOfWeek.NONE,
            )
        assertEquals(blocked(BlockReason.NOW, "b1", inst("2026-10-07T13:00:00")), RuleEvaluator.evaluate(ctx(wed, b)))
    }

    @Test
    fun `now block without start or duration is inert`() {
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, block(type = BlockType.NOW, durationMins = 30))))
        assertEquals(
            Decision.Allow,
            RuleEvaluator.evaluate(ctx(wed, block(type = BlockType.NOW, startedAt = inst("2026-10-07T12:00:00")))),
        )
    }

    // ---- LIMIT ----

    @Test
    fun `daily limit reached blocks until next midnight`() {
        val b = block(type = BlockType.LIMIT, limitMins = 60, period = LimitPeriod.DAILY)
        val c = ctx(wed, b, usage = mapOf(APP to AppUsageSnapshot(60 * MIN, 0)))
        assertEquals(blocked(BlockReason.LIMIT_DAILY, "b1", inst("2026-10-08T00:00:00")), RuleEvaluator.evaluate(c))
    }

    @Test
    fun `daily limit not reached allows`() {
        val b = block(type = BlockType.LIMIT, limitMins = 60, period = LimitPeriod.DAILY)
        val c = ctx(wed, b, usage = mapOf(APP to AppUsageSnapshot(60 * MIN - 1, 0)))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(c))
    }

    @Test
    fun `limit with no recorded usage allows`() {
        val b = block(type = BlockType.LIMIT, limitMins = 60, period = LimitPeriod.DAILY)
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, b)))
    }

    @Test
    fun `limit combines usage of all the block's apps`() {
        val b = block(type = BlockType.LIMIT, apps = setOf(APP, OTHER), limitMins = 60, period = LimitPeriod.DAILY)
        val usage = mapOf(APP to AppUsageSnapshot(20 * MIN, 0), OTHER to AppUsageSnapshot(40 * MIN, 0))
        assertEquals(
            blocked(BlockReason.LIMIT_DAILY, "b1", inst("2026-10-08T00:00:00")),
            RuleEvaluator.evaluate(ctx(wed, b, usage = usage)),
        )
        // Usage of apps outside the block does not count.
        val outside = mapOf(APP to AppUsageSnapshot(20 * MIN, 0), "x" to AppUsageSnapshot(99 * MIN, 0))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, b, usage = outside)))
    }

    @Test
    fun `hourly limit reached blocks until the next hour`() {
        val b = block(type = BlockType.LIMIT, limitMins = 10, period = LimitPeriod.HOURLY)
        val c = ctx(wed, b, usage = mapOf(APP to AppUsageSnapshot(0, 10 * MIN)))
        assertEquals(blocked(BlockReason.LIMIT_HOURLY, "b1", inst("2026-10-07T13:00:00")), RuleEvaluator.evaluate(c))
    }

    @Test
    fun `hourly limit uses this hour's usage and ignores the daily total`() {
        val b = block(type = BlockType.LIMIT, limitMins = 10, period = LimitPeriod.HOURLY)
        val c = ctx(wed, b, usage = mapOf(APP to AppUsageSnapshot(500 * MIN, 9 * MIN)))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(c))
    }

    @Test
    fun `hourly limit resets when the hour rolls over`() {
        val b = block(type = BlockType.LIMIT, limitMins = 10, period = LimitPeriod.HOURLY)
        val exhausted = ctx(at("2026-10-07T12:59:59"), b, usage = mapOf(APP to AppUsageSnapshot(0, 10 * MIN)))
        assertEquals(
            blocked(BlockReason.LIMIT_HOURLY, "b1", inst("2026-10-07T13:00:00")),
            RuleEvaluator.evaluate(exhausted),
        )
        val fresh = ctx(at("2026-10-07T13:00:00"), b, usage = mapOf(APP to AppUsageSnapshot(0, 0)))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(fresh))
    }

    @Test
    fun `limit on a day it does not apply to is ignored`() {
        val b =
            block(
                type = BlockType.LIMIT,
                limitMins = 1,
                period = LimitPeriod.DAILY,
                days = DaysOfWeek.of(DayOfWeek.MONDAY),
            )
        val c = ctx(wed, b, usage = mapOf(APP to AppUsageSnapshot(99 * MIN, 0)))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(c))
    }

    @Test
    fun `limit with no days never applies`() {
        val b = block(type = BlockType.LIMIT, limitMins = 1, period = LimitPeriod.DAILY, days = DaysOfWeek.NONE)
        val c = ctx(wed, b, usage = mapOf(APP to AppUsageSnapshot(99 * MIN, 0)))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(c))
    }

    @Test
    fun `limit missing its limit or period is inert`() {
        val usage = mapOf(APP to AppUsageSnapshot(99 * MIN, 99 * MIN))
        assertEquals(
            Decision.Allow,
            RuleEvaluator.evaluate(ctx(wed, block(type = BlockType.LIMIT, period = LimitPeriod.DAILY), usage = usage)),
        )
        assertEquals(
            Decision.Allow,
            RuleEvaluator.evaluate(ctx(wed, block(type = BlockType.LIMIT, limitMins = 1), usage = usage)),
        )
    }

    @Test
    fun `daily limit until follows the zone's midnight across a DST change`() {
        // 2026-03-29 is 23 hours long in Berlin.
        val b = block(type = BlockType.LIMIT, limitMins = 1, period = LimitPeriod.DAILY)
        val now = at("2026-03-29T10:00:00", BERLIN)
        val c = ctx(now, b, usage = mapOf(APP to AppUsageSnapshot(2 * MIN, 0)))
        assertEquals(
            blocked(BlockReason.LIMIT_DAILY, "b1", inst("2026-03-30T00:00:00", BERLIN)),
            RuleEvaluator.evaluate(c),
        )
    }

    @Test
    fun `hourly limit during the repeated autumn hour ends after one real hour`() {
        val b = block(type = BlockType.LIMIT, limitMins = 1, period = LimitPeriod.HOURLY)
        val firstTwo = ZonedDateTime.of(2026, 10, 25, 2, 30, 0, 0, BERLIN) // earlier offset (+02:00)
        val c = ctx(firstTwo, b, usage = mapOf(APP to AppUsageSnapshot(0, 5 * MIN)))
        assertEquals(
            blocked(
                BlockReason.LIMIT_HOURLY,
                "b1",
                firstTwo.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1).toInstant(),
            ),
            RuleEvaluator.evaluate(c),
        )
    }

    // ---- CYCLE ----

    @Test
    fun `cycle in rest blocks until it unlocks`() {
        val b = block(type = BlockType.CYCLE, useMins = 10, restMins = 30)
        val until = inst("2026-10-07T13:00:00")
        val c = ctx(wed, b, cycleStates = mapOf(CycleKey("b1", APP) to CycleState(0, until)))
        assertEquals(blocked(BlockReason.CYCLE_REST, "b1", until), RuleEvaluator.evaluate(c))
    }

    @Test
    fun `cycle whose rest is over allows`() {
        val b = block(type = BlockType.CYCLE, useMins = 10, restMins = 30)
        val c = ctx(wed, b, cycleStates = mapOf(CycleKey("b1", APP) to CycleState(0, wed.toInstant())))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(c))
    }

    @Test
    fun `cycle without state or lock allows`() {
        val b = block(type = BlockType.CYCLE, useMins = 10, restMins = 30)
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, b)))
        val c = ctx(wed, b, cycleStates = mapOf(CycleKey("b1", APP) to CycleState(5 * MIN, null)))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(c))
    }

    @Test
    fun `cycle state is per app`() {
        val b = block(type = BlockType.CYCLE, apps = setOf(APP, OTHER), useMins = 10, restMins = 30)
        val states = mapOf(CycleKey("b1", OTHER) to CycleState(0, inst("2026-10-07T13:00:00")))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, b, cycleStates = states)))
    }

    // ---- SCHEDULE ----

    @Test
    fun `daytime schedule is active inside the range`() {
        val b = block(type = BlockType.SCHEDULE, range = range("09:00–17:00"))
        assertEquals(
            blocked(BlockReason.SCHEDULE, "b1", inst("2026-10-07T17:00:00")),
            RuleEvaluator.evaluate(ctx(wed, b)),
        )
    }

    @Test
    fun `schedule start is inclusive and end is exclusive`() {
        val b = block(type = BlockType.SCHEDULE, range = range("09:00–17:00"))
        assertEquals(
            blocked(BlockReason.SCHEDULE, "b1", inst("2026-10-07T17:00:00")),
            RuleEvaluator.evaluate(ctx(at("2026-10-07T09:00:00"), b)),
        )
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(at("2026-10-07T17:00:00"), b)))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(at("2026-10-07T08:59:00"), b)))
    }

    @Test
    fun `schedule on an unselected day or with empty days is ignored`() {
        val monOnly =
            block(type = BlockType.SCHEDULE, range = range("09:00–17:00"), days = DaysOfWeek.of(DayOfWeek.MONDAY))
        val none = block(type = BlockType.SCHEDULE, range = range("09:00–17:00"), days = DaysOfWeek.NONE)
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, monOnly)))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, none)))
    }

    @Test
    fun `schedule without a range is inert`() {
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, block(type = BlockType.SCHEDULE))))
    }

    @Test
    fun `empty range never blocks`() {
        val b = block(type = BlockType.SCHEDULE, range = range("09:00–09:00"))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, b)))
    }

    @Test
    fun `overnight schedule before midnight ends the next morning`() {
        val b = block(type = BlockType.SCHEDULE, range = range("22:00–07:00"), days = DaysOfWeek.of(DayOfWeek.MONDAY))
        val c = ctx(at("2026-10-05T23:00:00"), b) // Monday
        assertEquals(blocked(BlockReason.SCHEDULE, "b1", inst("2026-10-06T07:00:00")), RuleEvaluator.evaluate(c))
    }

    @Test
    fun `overnight schedule after midnight belongs to the start day`() {
        val b = block(type = BlockType.SCHEDULE, range = range("22:00–07:00"), days = DaysOfWeek.of(DayOfWeek.MONDAY))
        val c = ctx(at("2026-10-06T03:00:00"), b) // Tuesday 03:00, started Monday
        assertEquals(blocked(BlockReason.SCHEDULE, "b1", inst("2026-10-06T07:00:00")), RuleEvaluator.evaluate(c))
        // Tuesday evening is not selected.
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(at("2026-10-06T23:00:00"), b)))
        // Monday 03:00 belongs to Sunday's occurrence, which is not selected.
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(at("2026-10-05T03:00:00"), b)))
    }

    @Test
    fun `overnight schedule across Sunday to Monday`() {
        val b = block(type = BlockType.SCHEDULE, range = range("22:00–07:00"), days = DaysOfWeek.of(DayOfWeek.SUNDAY))
        val sundayNight = ctx(at("2026-10-04T23:30:00"), b)
        assertEquals(
            blocked(BlockReason.SCHEDULE, "b1", inst("2026-10-05T07:00:00")),
            RuleEvaluator.evaluate(sundayNight),
        )
        val mondayMorning = ctx(at("2026-10-05T06:59:00"), b)
        assertEquals(
            blocked(BlockReason.SCHEDULE, "b1", inst("2026-10-05T07:00:00")),
            RuleEvaluator.evaluate(mondayMorning),
        )
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(at("2026-10-05T07:00:00"), b)))
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(at("2026-10-05T23:00:00"), b)))
    }

    @Test
    fun `overnight schedule across the spring DST change ends at the local end time`() {
        val b = block(type = BlockType.SCHEDULE, range = range("22:00–07:00"), days = DaysOfWeek.of(DayOfWeek.SATURDAY))
        val c = ctx(at("2026-03-28T23:00:00", BERLIN), b) // Saturday night; clocks jump at 02:00
        assertEquals(
            blocked(BlockReason.SCHEDULE, "b1", inst("2026-03-29T07:00:00", BERLIN)),
            RuleEvaluator.evaluate(c),
        )
    }

    // ---- precedence and combination ----

    @Test
    fun `latest until wins`() {
        val now = at("2026-10-07T12:30:00")
        val schedule = block(id = "s", type = BlockType.SCHEDULE, range = range("09:00–17:00"))
        val c = ctx(now, schedule, breakSession = breakUntil(inst("2026-10-07T12:40:00")))
        assertEquals(blocked(BlockReason.SCHEDULE, "s", inst("2026-10-07T17:00:00")), RuleEvaluator.evaluate(c))
    }

    @Test
    fun `manual lock beats everything because it never ends`() {
        val b = block(type = BlockType.NOW, durationMins = 600, startedAt = inst("2026-10-07T12:00:00"))
        val c = ctx(wed, b, lockedApps = listOf(lock()), focusSession = focusUntil(inst("2026-10-08T12:00:00")))
        assertEquals(blocked(BlockReason.MANUAL_LOCK, null, null), RuleEvaluator.evaluate(c))
    }

    @Test
    fun `equal until prefers break then focus then now then limit then cycle then schedule`() {
        val end = inst("2026-10-08T00:00:00")
        val nowB = block(id = "n", type = BlockType.NOW, durationMins = 180, startedAt = inst("2026-10-07T21:00:00"))
        val limit = block(id = "l", type = BlockType.LIMIT, limitMins = 1, period = LimitPeriod.DAILY)
        val cycle = block(id = "c", type = BlockType.CYCLE, useMins = 1, restMins = 1)
        val sched = block(id = "s", type = BlockType.SCHEDULE, range = range("20:00–00:00"))
        val all =
            ctx(
                at("2026-10-07T21:00:00"),
                sched,
                cycle,
                limit,
                nowB,
                breakSession = breakUntil(end),
                focusSession = focusUntil(end),
                usage = mapOf(APP to AppUsageSnapshot(5 * MIN, 0)),
                cycleStates = mapOf(CycleKey("c", APP) to CycleState(0, end)),
            )
        val expected =
            listOf(
                blocked(BlockReason.BREAK, null, end),
                blocked(BlockReason.FOCUS, null, end),
                blocked(BlockReason.NOW, "n", end),
                blocked(BlockReason.LIMIT_DAILY, "l", end),
                blocked(BlockReason.CYCLE_REST, "c", end),
                blocked(BlockReason.SCHEDULE, "s", end),
            )
        var current = all
        for (decision in expected) {
            assertEquals(decision, RuleEvaluator.evaluate(current))
            current =
                when (decision.reason) {
                    BlockReason.BREAK -> current.copy(breakSession = null)
                    BlockReason.FOCUS -> current.copy(focusSession = null)
                    else -> current.copy(blocks = current.blocks.filter { it.id != decision.blockId })
                }
        }
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(current))
    }

    @Test
    fun `disabled blocks are ignored`() {
        val b = block(type = BlockType.NOW, durationMins = 60, startedAt = inst("2026-10-07T12:00:00"), enabled = false)
        assertEquals(Decision.Allow, RuleEvaluator.evaluate(ctx(wed, b)))
    }

    @Test
    fun `earlier of two matching blocks loses`() {
        val short =
            block(id = "short", type = BlockType.NOW, durationMins = 40, startedAt = inst("2026-10-07T12:00:00"))
        val long = block(id = "long", type = BlockType.NOW, durationMins = 120, startedAt = inst("2026-10-07T12:00:00"))
        val expected = blocked(BlockReason.NOW, "long", inst("2026-10-07T14:00:00"))
        assertEquals(expected, RuleEvaluator.evaluate(ctx(wed, short, long)))
        assertEquals(expected, RuleEvaluator.evaluate(ctx(wed, long, short)))
    }

    // ---- nextEvaluationAt ----

    @Test
    fun `nextEvaluationAt is null with nothing to wait for`() {
        assertEquals(null, RuleEvaluator.nextEvaluationAt(ctx(wed)))
    }

    @Test
    fun `nextEvaluationAt is null for allowlisted apps`() {
        val b = block(type = BlockType.NOW, durationMins = 60, startedAt = inst("2026-10-07T12:00:00"))
        assertEquals(null, RuleEvaluator.nextEvaluationAt(ctx(wed, b, allowlist = setOf(APP))))
    }

    @Test
    fun `nextEvaluationAt covers break focus and allowance ends`() {
        val allow = TemporaryAllowance(APP, inst("2026-10-07T12:50:00"))
        val c =
            ctx(
                wed,
                breakSession = breakUntil(inst("2026-10-07T12:40:00")),
                focusSession = focusUntil(inst("2026-10-07T13:00:00")),
            )
        assertEquals(inst("2026-10-07T12:40:00"), RuleEvaluator.nextEvaluationAt(c))
        assertEquals(
            inst("2026-10-07T12:50:00"),
            RuleEvaluator.nextEvaluationAt(c.copy(breakSession = null, allowances = listOf(allow))),
        )
        assertEquals(inst("2026-10-07T13:00:00"), RuleEvaluator.nextEvaluationAt(c.copy(breakSession = null)))
        // Allowances of other apps and moments in the past are ignored.
        val other = TemporaryAllowance(OTHER, inst("2026-10-07T12:31:00"))
        assertEquals(
            null,
            RuleEvaluator.nextEvaluationAt(
                ctx(wed, allowances = listOf(other), breakSession = breakUntil(inst("2026-10-07T12:00:00"))),
            ),
        )
    }

    @Test
    fun `nextEvaluationAt for NOW covers a future start and the end`() {
        val future = block(type = BlockType.NOW, durationMins = 30, startedAt = inst("2026-10-07T13:00:00"))
        assertEquals(inst("2026-10-07T13:00:00"), RuleEvaluator.nextEvaluationAt(ctx(wed, future)))
        val running = block(type = BlockType.NOW, durationMins = 30, startedAt = inst("2026-10-07T12:20:00"))
        assertEquals(inst("2026-10-07T12:50:00"), RuleEvaluator.nextEvaluationAt(ctx(wed, running)))
        val incomplete = block(type = BlockType.NOW)
        assertEquals(null, RuleEvaluator.nextEvaluationAt(ctx(wed, incomplete)))
    }

    @Test
    fun `nextEvaluationAt ignores disabled blocks and other apps`() {
        val disabled =
            block(type = BlockType.NOW, durationMins = 60, startedAt = inst("2026-10-07T12:00:00"), enabled = false)
        val other =
            block(
                id = "o",
                type = BlockType.NOW,
                apps = setOf(OTHER),
                durationMins = 60,
                startedAt = inst("2026-10-07T12:00:00"),
            )
        assertEquals(null, RuleEvaluator.nextEvaluationAt(ctx(wed, disabled, other)))
    }

    @Test
    fun `nextEvaluationAt for a schedule finds the next boundary`() {
        val day = block(type = BlockType.SCHEDULE, range = range("09:00–17:00"))
        assertEquals(inst("2026-10-07T17:00:00"), RuleEvaluator.nextEvaluationAt(ctx(wed, day)))
        val evening = at("2026-10-07T18:00:00")
        assertEquals(inst("2026-10-08T09:00:00"), RuleEvaluator.nextEvaluationAt(ctx(evening, day)))
        val overnight =
            block(type = BlockType.SCHEDULE, range = range("22:00–07:00"), days = DaysOfWeek.of(DayOfWeek.MONDAY))
        assertEquals(
            inst("2026-10-06T07:00:00"),
            RuleEvaluator.nextEvaluationAt(ctx(at("2026-10-05T23:00:00"), overnight)),
        )
        assertEquals(
            inst("2026-10-12T22:00:00"),
            RuleEvaluator.nextEvaluationAt(ctx(at("2026-10-06T08:00:00"), overnight)),
        )
    }

    @Test
    fun `nextEvaluationAt for a schedule without usable range is null`() {
        assertEquals(null, RuleEvaluator.nextEvaluationAt(ctx(wed, block(type = BlockType.SCHEDULE))))
        assertEquals(
            null,
            RuleEvaluator.nextEvaluationAt(ctx(wed, block(type = BlockType.SCHEDULE, range = range("09:00–09:00")))),
        )
        val noDays = block(type = BlockType.SCHEDULE, range = range("09:00–17:00"), days = DaysOfWeek.NONE)
        assertEquals(null, RuleEvaluator.nextEvaluationAt(ctx(wed, noDays)))
    }

    @Test
    fun `nextEvaluationAt for a limit is the exhaustion at current usage`() {
        val b = block(type = BlockType.LIMIT, limitMins = 60, period = LimitPeriod.DAILY)
        val c = ctx(wed, b, usage = mapOf(APP to AppUsageSnapshot(50 * MIN, 0)))
        assertEquals(inst("2026-10-07T12:40:00"), RuleEvaluator.nextEvaluationAt(c))
    }

    @Test
    fun `nextEvaluationAt for an exhausted limit is the reset`() {
        val daily = block(type = BlockType.LIMIT, limitMins = 60, period = LimitPeriod.DAILY)
        val c = ctx(wed, daily, usage = mapOf(APP to AppUsageSnapshot(60 * MIN, 0)))
        assertEquals(inst("2026-10-08T00:00:00"), RuleEvaluator.nextEvaluationAt(c))
        val hourly = block(type = BlockType.LIMIT, limitMins = 5, period = LimitPeriod.HOURLY)
        val h = ctx(wed, hourly, usage = mapOf(APP to AppUsageSnapshot(0, 5 * MIN)))
        assertEquals(inst("2026-10-07T13:00:00"), RuleEvaluator.nextEvaluationAt(h))
    }

    @Test
    fun `nextEvaluationAt for a limit on another day is the day boundary`() {
        val b =
            block(
                type = BlockType.LIMIT,
                limitMins = 60,
                period = LimitPeriod.DAILY,
                days = DaysOfWeek.of(DayOfWeek.THURSDAY),
            )
        assertEquals(inst("2026-10-08T00:00:00"), RuleEvaluator.nextEvaluationAt(ctx(wed, b)))
    }

    @Test
    fun `nextEvaluationAt for an incomplete limit is null`() {
        assertEquals(
            null,
            RuleEvaluator.nextEvaluationAt(ctx(wed, block(type = BlockType.LIMIT, period = LimitPeriod.DAILY))),
        )
        assertEquals(null, RuleEvaluator.nextEvaluationAt(ctx(wed, block(type = BlockType.LIMIT, limitMins = 5))))
    }

    @Test
    fun `nextEvaluationAt for a cycle is unlock or exhaustion`() {
        val b = block(type = BlockType.CYCLE, useMins = 10, restMins = 30)
        val resting =
            ctx(wed, b, cycleStates = mapOf(CycleKey("b1", APP) to CycleState(0, inst("2026-10-07T13:00:00"))))
        assertEquals(inst("2026-10-07T13:00:00"), RuleEvaluator.nextEvaluationAt(resting))
        val using = ctx(wed, b, cycleStates = mapOf(CycleKey("b1", APP) to CycleState(4 * MIN, null)))
        assertEquals(inst("2026-10-07T12:36:00"), RuleEvaluator.nextEvaluationAt(using))
        assertEquals(inst("2026-10-07T12:40:00"), RuleEvaluator.nextEvaluationAt(ctx(wed, b)))
        val over = ctx(wed, b, cycleStates = mapOf(CycleKey("b1", APP) to CycleState(99 * MIN, null)))
        assertEquals(null, RuleEvaluator.nextEvaluationAt(over)) // already due: not strictly after now
        assertEquals(null, RuleEvaluator.nextEvaluationAt(ctx(wed, block(type = BlockType.CYCLE))))
    }

    @Test
    fun `nextEvaluationAt picks the earliest of several`() {
        val limit = block(id = "l", type = BlockType.LIMIT, limitMins = 60, period = LimitPeriod.DAILY)
        val nowB = block(id = "n", type = BlockType.NOW, durationMins = 5, startedAt = inst("2026-10-07T12:29:00"))
        val c = ctx(wed, limit, nowB, usage = mapOf(APP to AppUsageSnapshot(10 * MIN, 0)))
        assertEquals(inst("2026-10-07T12:34:00"), RuleEvaluator.nextEvaluationAt(c))
    }
}
