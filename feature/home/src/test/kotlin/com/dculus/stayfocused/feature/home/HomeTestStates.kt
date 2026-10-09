package com.dculus.stayfocused.feature.home

import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.KnownApps
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.ledLevels
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

internal fun readyGauge(
    totalMins: Int = 147,
    avgMins: Int? = 139,
    currentHour: Int = 10,
) = GaugeUi.Ready(
    totalMins = totalMins,
    avgMins = avgMins,
    levels = ledLevels(PrototypeHours, HOME_LED_ROWS, currentHour),
    launches = 63,
    unlocks = 48,
    blocked = 12,
)

internal fun homeState(
    gauge: GaugeUi = readyGauge(),
    blocks: List<HomeBlockUi> = emptyList(),
    devices: List<LinkedDevice> = emptyList(),
    firstName: String? = "Sam",
    aiAvailable: Boolean = true,
) = HomeUiState(
    header = headerFor(ZonedDateTime.of(2026, 10, 6, 10, 42, 0, 0, ZoneOffset.UTC), firstName, Locale.ENGLISH),
    gauge = gauge,
    blocks = blocks,
    devices = devices,
    aiAvailable = aiAvailable,
)

/** Rows for [blocks] with the prototype names as app labels. */
internal fun blockRows(vararg blocks: Block) =
    blocks.map { b ->
        val labels =
            b.apps.map { p ->
                KnownApps.packages.entries
                    .first { it.value == p }
                    .key
                    .replaceFirstChar { c -> c.uppercase() }
            }
        HomeBlockUi(b, labels)
    }

/** Records what the dashboard's buttons do. */
internal class Taps {
    val log = mutableListOf<String>()
    val started = mutableListOf<Int>()
    val toggled = mutableListOf<Pair<String, Boolean>>()

    fun actions() =
        HomeActions(
            destinations =
                HomeDestinations(
                    onOpenAccount = { log += "account" },
                    onNewBlock = { log += "newBlock" },
                    onAiDescribe = { log += "ai" },
                    onOpenInsights = { log += "insights" },
                    onOpenBlock = { log += "block" },
                    onOpenDevices = { log += "devices" },
                    onOpenDevice = { log += "device:$it" },
                ),
            onAllowUsageAccess = { log += "allowUsage" },
            onSetBlockEnabled = { id, on -> toggled += id to on },
            onStartBreak = { started += it },
            onEndBreak = { log += "endBreak" },
        )
}
