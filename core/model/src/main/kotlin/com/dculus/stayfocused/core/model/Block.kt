package com.dculus.stayfocused.core.model

import java.time.Instant

enum class BlockType { LIMIT, CYCLE, SCHEDULE, NOW }

enum class LimitPeriod { DAILY, HOURLY }

/**
 * How a "use, then lock" block counts time: [PER_APP] gives every app its own use/lock window, [TOTAL] shares one
 * window between all apps of the block.
 */
enum class CycleScope { PER_APP, TOTAL }

/** Package a [CycleScope.TOTAL] cycle state is stored under: the one shared window of the block. */
const val CYCLE_TOTAL_PKG = ""

enum class BlockSource { MANUAL, TEMPLATE, AI }

sealed interface BlockTarget {
    data object ThisPhone : BlockTarget

    data class Device(
        val deviceId: String,
    ) : BlockTarget
}

data class Block(
    val id: String,
    val target: BlockTarget,
    val type: BlockType,
    val name: String,
    /** Package names. */
    val apps: Set<String>,
    /** LIMIT */
    val limitMins: Int?,
    /** LIMIT */
    val period: LimitPeriod?,
    /** CYCLE */
    val useMins: Int?,
    /** CYCLE */
    val restMins: Int?,
    /** SCHEDULE; may cross midnight. */
    val range: TimeRange?,
    /** NOW */
    val durationMins: Int?,
    /** NOW */
    val startedAt: Instant?,
    /** Ignored for NOW. */
    val days: DaysOfWeek,
    val enabled: Boolean,
    val createdAt: Instant,
    val source: BlockSource,
    /** CYCLE */
    val cycleScope: CycleScope = CycleScope.PER_APP,
)
