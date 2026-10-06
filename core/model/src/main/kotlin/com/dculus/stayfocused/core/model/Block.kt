package com.dculus.stayfocused.core.model

import java.time.Instant

enum class BlockType { LIMIT, CYCLE, SCHEDULE, NOW }

enum class LimitPeriod { DAILY, HOURLY }

enum class BlockSource { MANUAL, TEMPLATE, AI }

sealed interface BlockTarget {
    data object ThisPhone : BlockTarget

    data class Device(val deviceId: String) : BlockTarget
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
)
