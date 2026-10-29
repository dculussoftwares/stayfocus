package com.dculus.stayfocused.core.blocking.evaluator

import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.LimitPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Pure decision logic: may [EvaluationContext.pkg] be in the foreground right now? No Android imports. */
@Suppress("ReturnCount", "TooManyFunctions") // guard clauses keep each rule flat and readable
object RuleEvaluator {
    private const val MS_PER_MIN = 60_000L
    private const val SCHEDULE_LOOKAHEAD_DAYS = 8L
    private const val ONE_MIN_MS = MS_PER_MIN

    /** Slack so a warning is not missed when the wake-up lands a few ms before its threshold. */
    private const val WARNING_SLACK_MS = 1_000L

    /** Order in which equally long blocks are reported. */
    private val TIE_BREAK =
        listOf(
            BlockReason.MANUAL_LOCK,
            BlockReason.ALL_LOCKED,
            BlockReason.BREAK,
            BlockReason.FOCUS,
            BlockReason.NOW,
            BlockReason.LIMIT_DAILY,
            BlockReason.LIMIT_HOURLY,
            BlockReason.CYCLE_REST,
            BlockReason.SCHEDULE,
        )

    fun evaluate(ctx: EvaluationContext): Decision {
        val now = ctx.now.toInstant()
        if (ctx.pkg in ctx.allowlist) return Decision.Allow
        if (ctx.allowances.any { it.pkg == ctx.pkg && it.until > now }) return Decision.Allow
        // Candidates are listed in tie-break priority; the strictly latest `until` wins.
        return candidates(ctx).fold(null as Decision.Block?) { best, c ->
            if (best == null || isLater(c.until, best.until)) c else best
        } ?: Decision.Allow
    }

    /** The earliest instant after `now` at which the decision for the app could change, or null if never. */
    fun nextEvaluationAt(ctx: EvaluationContext): Instant? {
        val now = ctx.now.toInstant()
        if (ctx.pkg in ctx.allowlist) return null
        val times = mutableListOf<Instant?>()
        ctx.allowances.filter { it.pkg == ctx.pkg }.forEach { times += it.until }
        // A manual lock (or "lock all apps") only gives way to an allowance, so nothing else can change the decision.
        if (ctx.allLocked ||
            ctx.lockedApps.any { it.pkg == ctx.pkg }
        ) {
            return times.filterNotNull().filter { it > now }.minOrNull()
        }
        times += ctx.breakSession?.endsAt
        times += ctx.focusSession?.endsAt
        ctx.blocks.filter { it.enabled && ctx.pkg in it.apps }.forEach { times += blockChangeTimes(ctx, it) }
        return times.filterNotNull().filter { it > now }.minOrNull()
    }

    /**
     * The warnings each limit or use-then-lock block with a budget has reached for [EvaluationContext.pkg]: the
     * most urgent kind per block. Empty while the app is blocked or allowlisted. No state: the caller remembers
     * what it already showed.
     */
    fun usageWarnings(ctx: EvaluationContext): List<UsageWarning> {
        if (ctx.pkg in ctx.allowlist || ctx.allLocked || ctx.lockedApps.any { it.pkg == ctx.pkg }) return emptyList()
        return ctx.blocks
            .filter { it.enabled && ctx.pkg in it.apps }
            .mapNotNull { block ->
                val (total, remaining) = budget(ctx, block) ?: return@mapNotNull null
                val kind =
                    when {
                        total > ONE_MIN_MS + WARNING_SLACK_MS && remaining <= ONE_MIN_MS + WARNING_SLACK_MS -> {
                            WarningKind.ONE_MIN
                        }

                        total > 2 * ONE_MIN_MS && remaining <= total / 2 + WARNING_SLACK_MS -> {
                            WarningKind.HALF
                        }

                        else -> {
                            null
                        }
                    }
                kind?.let { UsageWarning(block.id, it, remaining) }
            }
    }

    /** (total, remaining) milliseconds of the block's budget right now, or null if it has none or is spent. */
    private fun budget(
        ctx: EvaluationContext,
        block: Block,
    ): Pair<Long, Long>? {
        val (total, remaining) =
            when (block.type) {
                BlockType.LIMIT -> {
                    val limitMins = block.limitMins ?: return null
                    val period = block.period ?: return null
                    if (ctx.now.dayOfWeek !in block.days) return null
                    val total = limitMins * MS_PER_MIN
                    total to total - combinedUsedMs(ctx, block, period)
                }

                BlockType.CYCLE -> {
                    val state = ctx.cycleStates[block.cycleKey(ctx.pkg)] ?: CycleState()
                    if (state.lockedUntil?.let { it > ctx.now.toInstant() } == true) return null
                    val total = (block.useMins ?: return null) * MS_PER_MIN
                    total to total - state.usedMs
                }

                else -> {
                    return null
                }
            }
        return if (remaining > 0) total to remaining else null
    }

    /** Instants at which the budget crosses a warning threshold, assuming the app stays in front. */
    private fun warningTimes(
        ctx: EvaluationContext,
        block: Block,
    ): List<Instant> {
        val (total, remaining) = budget(ctx, block) ?: return emptyList()
        val now = ctx.now.toInstant()
        return listOfNotNull(
            (remaining - ONE_MIN_MS).takeIf { total > ONE_MIN_MS + WARNING_SLACK_MS },
            (remaining - total / 2).takeIf { total > 2 * ONE_MIN_MS },
        ).filter { it > 0 }
            .map { now.plusMillis(it) }
    }

    private fun isLater(
        a: Instant?,
        b: Instant?,
    ): Boolean = b != null && (a == null || a > b)

    private fun candidates(ctx: EvaluationContext): List<Decision.Block> {
        val now = ctx.now.toInstant()
        val out = mutableListOf<Decision.Block>()
        if (ctx.lockedApps.any { it.pkg == ctx.pkg }) {
            out += Decision.Block(BlockReason.MANUAL_LOCK, null, null)
        }
        if (ctx.allLocked) out += Decision.Block(BlockReason.ALL_LOCKED, null, null)
        ctx.breakSession?.takeIf { now < it.endsAt }?.let {
            out += Decision.Block(BlockReason.BREAK, null, it.endsAt)
        }
        ctx.focusSession?.takeIf { now < it.endsAt }?.let {
            out += Decision.Block(BlockReason.FOCUS, null, it.endsAt)
        }
        for (block in ctx.blocks) {
            if (!block.enabled || ctx.pkg !in block.apps) continue
            val match =
                when (block.type) {
                    BlockType.NOW -> nowBlock(ctx, block)
                    BlockType.LIMIT -> limitBlock(ctx, block)
                    BlockType.CYCLE -> cycleBlock(ctx, block)
                    BlockType.SCHEDULE -> scheduleBlock(ctx, block)
                }
            if (match != null) out += match
        }
        return out.sortedBy { TIE_BREAK.indexOf(it.reason) }
    }

    private fun nowEnd(block: Block): Instant? {
        val start = block.startedAt ?: return null
        val mins = block.durationMins ?: return null
        return start.plusSeconds(mins * 60L)
    }

    private fun nowBlock(
        ctx: EvaluationContext,
        block: Block,
    ): Decision.Block? {
        val now = ctx.now.toInstant()
        val start = block.startedAt ?: return null
        val end = nowEnd(block) ?: return null
        return if (start <= now && now < end) Decision.Block(BlockReason.NOW, block.id, end) else null
    }

    private fun periodEnd(
        now: ZonedDateTime,
        period: LimitPeriod,
    ): Instant =
        when (period) {
            LimitPeriod.DAILY -> {
                now
                    .toLocalDate()
                    .plusDays(1)
                    .atStartOfDay(now.zone)
                    .toInstant()
            }

            LimitPeriod.HOURLY -> {
                now.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()
            }
        }

    private fun combinedUsedMs(
        ctx: EvaluationContext,
        block: Block,
        period: LimitPeriod,
    ): Long =
        block.apps.sumOf { app ->
            val u = ctx.usage[app]
            when (period) {
                LimitPeriod.DAILY -> u?.todayMs ?: 0L
                LimitPeriod.HOURLY -> u?.thisHourMs ?: 0L
            }
        }

    private fun limitBlock(
        ctx: EvaluationContext,
        block: Block,
    ): Decision.Block? {
        val limitMins = block.limitMins ?: return null
        val period = block.period ?: return null
        if (ctx.now.dayOfWeek !in block.days) return null
        if (combinedUsedMs(ctx, block, period) < limitMins * MS_PER_MIN) return null
        val reason = if (period == LimitPeriod.DAILY) BlockReason.LIMIT_DAILY else BlockReason.LIMIT_HOURLY
        return Decision.Block(reason, block.id, periodEnd(ctx.now, period))
    }

    private fun cycleBlock(
        ctx: EvaluationContext,
        block: Block,
    ): Decision.Block? {
        val lockedUntil = ctx.cycleStates[block.cycleKey(ctx.pkg)]?.lockedUntil ?: return null
        return if (lockedUntil > ctx.now.toInstant()) {
            Decision.Block(BlockReason.CYCLE_REST, block.id, lockedUntil)
        } else {
            null
        }
    }

    private fun scheduleBlock(
        ctx: EvaluationContext,
        block: Block,
    ): Decision.Block? {
        val range = block.range ?: return null
        val now = ctx.now.toInstant()
        val today = ctx.now.toLocalDate()
        // Overnight ranges belong to their start day, so yesterday's occurrence may still be running.
        val end =
            listOf(today.minusDays(1), today)
                .filter { it.dayOfWeek in block.days }
                .firstNotNullOfOrNull { day ->
                    val endDay = if (range.crossesMidnight) day.plusDays(1) else day
                    val start = localInstant(day, range.start, ctx.now.zone)
                    val end = localInstant(endDay, range.end, ctx.now.zone)
                    end.takeIf { start <= now && now < end }
                } ?: return null
        return Decision.Block(BlockReason.SCHEDULE, block.id, end)
    }

    /**
     * The instant a wall-clock time falls on. A time skipped by a spring-forward gap resolves to the moment the
     * clocks jump; a repeated autumn time resolves to its first occurrence.
     */
    private fun localInstant(
        date: LocalDate,
        time: LocalTime,
        zone: ZoneId,
    ): Instant {
        val local = LocalDateTime.of(date, time)
        val transition = zone.rules.getTransition(local)
        return if (transition != null && transition.isGap) transition.instant else local.atZone(zone).toInstant()
    }

    private fun blockChangeTimes(
        ctx: EvaluationContext,
        block: Block,
    ): List<Instant?> =
        when (block.type) {
            BlockType.NOW -> listOf(block.startedAt, nowEnd(block))
            BlockType.SCHEDULE -> scheduleBoundaries(ctx, block)
            BlockType.LIMIT -> limitChangeTimes(ctx, block)
            BlockType.CYCLE -> cycleChangeTimes(ctx, block)
        }

    private fun scheduleBoundaries(
        ctx: EvaluationContext,
        block: Block,
    ): List<Instant?> {
        val range = block.range ?: return emptyList()
        if (range.start == range.end) return emptyList()
        val today = ctx.now.toLocalDate()
        return (-1L..SCHEDULE_LOOKAHEAD_DAYS)
            .map { today.plusDays(it) }
            .filter { it.dayOfWeek in block.days }
            .flatMap { day ->
                val endDay = if (range.crossesMidnight) day.plusDays(1) else day
                listOf(
                    localInstant(day, range.start, ctx.now.zone),
                    localInstant(endDay, range.end, ctx.now.zone),
                )
            }
    }

    private fun limitChangeTimes(
        ctx: EvaluationContext,
        block: Block,
    ): List<Instant?> {
        val limitMins = block.limitMins ?: return emptyList()
        val period = block.period ?: return emptyList()
        if (block.days.isEmpty) return emptyList()
        val boundary = periodEnd(ctx.now, period)
        if (ctx.now.dayOfWeek !in block.days) return listOf(boundary)
        val remainingMs = limitMins * MS_PER_MIN - combinedUsedMs(ctx, block, period)
        // Exhaustion assumes the app stays in the foreground; once exhausted only the reset matters.
        return if (remainingMs > 0) {
            listOf(ctx.now.toInstant().plusMillis(remainingMs), boundary) + warningTimes(ctx, block)
        } else {
            listOf(boundary)
        }
    }

    private fun cycleChangeTimes(
        ctx: EvaluationContext,
        block: Block,
    ): List<Instant?> {
        val now = ctx.now.toInstant()
        val state = ctx.cycleStates[block.cycleKey(ctx.pkg)] ?: CycleState()
        val lockedUntil = state.lockedUntil
        if (lockedUntil != null && lockedUntil > now) return listOf(lockedUntil)
        val useMs = (block.useMins ?: return emptyList()) * MS_PER_MIN
        return listOf(now.plusMillis((useMs - state.usedMs).coerceAtLeast(0))) + warningTimes(ctx, block)
    }
}
