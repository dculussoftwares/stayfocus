package com.dculus.stayfocused.core.blocking.evaluator

import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.LimitPeriod
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Pure decision logic: may [EvaluationContext.pkg] be in the foreground right now? No Android imports. */
@Suppress("ReturnCount", "TooManyFunctions") // guard clauses keep each rule flat and readable
object RuleEvaluator {
    private const val MS_PER_MIN = 60_000L
    private const val SCHEDULE_LOOKAHEAD_DAYS = 8L

    /** Order in which equally long blocks are reported. */
    private val TIE_BREAK =
        listOf(
            BlockReason.MANUAL_LOCK,
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
        if (ctx.lockedApps.any { it.pkg == ctx.pkg }) return null
        val times = mutableListOf<Instant?>()
        ctx.allowances.filter { it.pkg == ctx.pkg }.forEach { times += it.until }
        times += ctx.breakSession?.endsAt
        times += ctx.focusSession?.endsAt
        ctx.blocks.filter { it.enabled && ctx.pkg in it.apps }.forEach { times += blockChangeTimes(ctx, it) }
        return times.filterNotNull().filter { it > now }.minOrNull()
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
        val lockedUntil = ctx.cycleStates[CycleKey(block.id, ctx.pkg)]?.lockedUntil ?: return null
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
        val now = ctx.now
        val time = now.toLocalTime()
        if (time !in range) return null
        // Overnight ranges belong to their start day: after midnight the start day is yesterday.
        val afterMidnightPart = range.crossesMidnight && time < range.end
        val startDay = if (afterMidnightPart) now.toLocalDate().minusDays(1) else now.toLocalDate()
        if (startDay.dayOfWeek !in block.days) return null
        val endDay = if (range.crossesMidnight) startDay.plusDays(1) else startDay
        val end = scheduleBoundary(endDay, range.end, now)
        return Decision.Block(BlockReason.SCHEDULE, block.id, end)
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

    /** Resolve a local boundary without moving it through a gap or choosing the wrong overlap. */
    private fun scheduleBoundary(
        day: java.time.LocalDate,
        time: java.time.LocalTime,
        reference: ZonedDateTime,
        useReferenceOccurrence: Boolean = true,
    ): Instant {
        val zone = reference.zone
        val local = java.time.LocalDateTime.of(day, time)
        val offsets = zone.rules.getValidOffsets(local)
        if (offsets.isEmpty()) {
            // A skipped local time takes effect at the instant the clock jumps.
            return zone.rules.getTransition(local).instant
        }
        val preferred = if (useReferenceOccurrence) reference.offset.takeIf { it in offsets } else null
        return ZonedDateTime.ofLocal(local, zone, preferred ?: offsets.first()).toInstant()
    }

    private fun scheduleBoundaryCandidates(
        day: java.time.LocalDate,
        time: java.time.LocalTime,
        zone: java.time.ZoneId,
    ): List<Instant> {
        val local = java.time.LocalDateTime.of(day, time)
        val offsets = zone.rules.getValidOffsets(local)
        return if (offsets.isEmpty()) {
            // A skipped local time takes effect at the instant the clock jumps.
            listOf(zone.rules.getTransition(local).instant)
        } else {
            offsets.map { ZonedDateTime.ofLocal(local, zone, it).toInstant() }
        }
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
                scheduleBoundaryCandidates(day, range.start, ctx.now.zone) +
                    scheduleBoundaryCandidates(endDay, range.end, ctx.now.zone)
            }
    }

    private fun limitChangeTimes(
        ctx: EvaluationContext,
        block: Block,
    ): List<Instant?> {
        val limitMins = block.limitMins ?: return emptyList()
        val period = block.period ?: return emptyList()
        if (block.days.isEmpty) return emptyList()
        if (ctx.now.dayOfWeek !in block.days) {
            val nextDay = (1L..7L)
                .map { ctx.now.toLocalDate().plusDays(it) }
                .first { it.dayOfWeek in block.days }
            return listOf(nextDay.atStartOfDay(ctx.now.zone).toInstant())
        }
        val boundary = periodEnd(ctx.now, period)
        val remainingMs = limitMins * MS_PER_MIN - combinedUsedMs(ctx, block, period)
        // Exhaustion assumes the app stays in the foreground; once exhausted only the reset matters.
        return if (remainingMs > 0) listOf(ctx.now.toInstant().plusMillis(remainingMs), boundary) else listOf(boundary)
    }

    private fun cycleChangeTimes(
        ctx: EvaluationContext,
        block: Block,
    ): List<Instant?> {
        val now = ctx.now.toInstant()
        val state = ctx.cycleStates[CycleKey(block.id, ctx.pkg)] ?: CycleState()
        val lockedUntil = state.lockedUntil
        if (lockedUntil != null && lockedUntil > now) return listOf(lockedUntil)
        val useMs = (block.useMins ?: return emptyList()) * MS_PER_MIN
        return listOf(now.plusMillis((useMs - state.usedMs).coerceAtLeast(0)))
    }
}
