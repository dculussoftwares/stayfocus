package com.dculus.stayfocused.feature.block

import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.KnownApps
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.TimeRange
import java.time.Instant

fun pkg(id: String): String = KnownApps.packages.getValue(id)

private val Epoch: Instant = Instant.parse("2026-01-01T00:00:00Z")

fun cycleBlock(
    id: String = "b1",
    target: BlockTarget = BlockTarget.ThisPhone,
    enabled: Boolean = true,
) = Block(
    id = id,
    target = target,
    type = BlockType.CYCLE,
    name = "Mindful scrolling",
    apps = setOf(pkg("instagram"), pkg("youtube")),
    limitMins = null,
    period = null,
    useMins = 10,
    restMins = 30,
    range = null,
    durationMins = null,
    startedAt = null,
    days = DaysOfWeek.ALL,
    enabled = enabled,
    createdAt = Epoch,
    source = BlockSource.TEMPLATE,
)

fun limitBlock(
    id: String = "b2",
    target: BlockTarget = BlockTarget.ThisPhone,
    enabled: Boolean = true,
) = Block(
    id = id,
    target = target,
    type = BlockType.LIMIT,
    name = "Social limit",
    apps = setOf(pkg("reddit"), pkg("x")),
    limitMins = 30,
    period = LimitPeriod.DAILY,
    useMins = null,
    restMins = null,
    range = null,
    durationMins = null,
    startedAt = null,
    days = DaysOfWeek.ALL,
    enabled = enabled,
    createdAt = Epoch.plusSeconds(1),
    source = BlockSource.MANUAL,
)

fun scheduleBlock(
    id: String = "b3",
    target: BlockTarget = BlockTarget.ThisPhone,
    enabled: Boolean = false,
) = Block(
    id = id,
    target = target,
    type = BlockType.SCHEDULE,
    name = "Work hours",
    apps = setOf(pkg("instagram"), pkg("youtube"), pkg("reddit")),
    limitMins = null,
    period = null,
    useMins = null,
    restMins = null,
    range = TimeRange.parse("09:00–17:00"),
    durationMins = null,
    startedAt = null,
    days = DaysOfWeek.WEEKDAYS,
    enabled = enabled,
    createdAt = Epoch.plusSeconds(2),
    source = BlockSource.MANUAL,
)

fun device(
    id: String = "d1",
    name: String = "Aarav's phone",
) = LinkedDevice(id, name, "Pixel 6a", 80, null, true, Epoch, emptyList(), emptyList(), null)
