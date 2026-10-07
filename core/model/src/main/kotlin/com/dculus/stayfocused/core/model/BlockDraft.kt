package com.dculus.stayfocused.core.model

/**
 * Wizard state for a block being created (prototype `wiz*` state); defaults match the prototype `openWizard`.
 * [fromAi] drives the "Check your block" title and [aiNote] is the one-sentence restatement of the rule.
 */
data class BlockDraft(
    val target: BlockTarget = BlockTarget.ThisPhone,
    val type: BlockType = BlockType.LIMIT,
    /** Package names. */
    val apps: Set<String> = setOf(KnownApps.packages.getValue("instagram"), KnownApps.packages.getValue("youtube")),
    val period: LimitPeriod = LimitPeriod.DAILY,
    val mins: Int = 30,
    val use: Int = 10,
    val rest: Int = 30,
    val range: TimeRange = TimeRange.parse("09:00–17:00"),
    val now: Int = 60,
    val days: DaysOfWeek = DaysOfWeek.WEEKDAYS,
    val fromAi: Boolean = false,
    val aiNote: String = "",
)
