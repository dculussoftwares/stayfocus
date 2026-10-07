package com.dculus.stayfocused.core.data.db

import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.LockedApp
import com.dculus.stayfocused.core.model.TimeRange

fun BlockTarget.toKey(): String =
    when (this) {
        BlockTarget.ThisPhone -> {
            TARGET_ME
        }

        is BlockTarget.Device -> {
            require(deviceId != TARGET_ME) { "deviceId must not equal the reserved this-phone key '$TARGET_ME'" }
            deviceId
        }
    }

fun String.toBlockTarget(): BlockTarget = if (this == TARGET_ME) BlockTarget.ThisPhone else BlockTarget.Device(this)

fun Block.toEntity(): BlockEntity =
    BlockEntity(
        id = id,
        target = target.toKey(),
        type = type,
        name = name,
        limitMins = limitMins,
        period = period,
        useMins = useMins,
        restMins = restMins,
        rangeStart = range?.start,
        rangeEnd = range?.end,
        durationMins = durationMins,
        startedAt = startedAt,
        days = days,
        enabled = enabled,
        createdAt = createdAt,
        source = source,
    )

fun Block.toAppEntities(): List<BlockAppEntity> = apps.map { BlockAppEntity(blockId = id, pkg = it) }

fun BlockWithApps.toModel(): Block =
    Block(
        id = block.id,
        target = block.target.toBlockTarget(),
        type = block.type,
        name = block.name,
        apps = apps.mapTo(linkedSetOf()) { it.pkg },
        limitMins = block.limitMins,
        period = block.period,
        useMins = block.useMins,
        restMins = block.restMins,
        range = block.rangeStart?.let { start -> block.rangeEnd?.let { end -> TimeRange(start, end) } },
        durationMins = block.durationMins,
        startedAt = block.startedAt,
        days = block.days,
        enabled = block.enabled,
        createdAt = block.createdAt,
        source = block.source,
    )

fun LockedApp.toEntity(): LockedAppEntity = LockedAppEntity(pkg = pkg, target = target.toKey(), since = since)

fun LockedAppEntity.toModel(): LockedApp = LockedApp(pkg = pkg, target = target.toBlockTarget(), since = since)
