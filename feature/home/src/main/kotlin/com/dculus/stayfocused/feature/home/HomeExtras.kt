@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.formatMinutes
import com.dculus.stayfocused.core.ui.components.V5Card
import com.dculus.stayfocused.core.ui.components.V5Toggle
import com.dculus.stayfocused.core.ui.components.blockDetail
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

internal const val HOME_STATS_CARD_TAG = "home_stats_card"
internal const val HOME_MANAGE_TAG = "home_manage"
internal const val HOME_BLOCKS_EMPTY_TAG = "home_blocks_empty"

// Final v5 form of the shipped Home content the prototype does not show (M13-08): two v5 cards after the child
// cards, built from v5 components only. Kept (not removed) because no other screen shows the average line, the
// launches/unlocks counts or the on/off switches next to the day's usage.

/** Today against the average, plus the launches and unlocks counts. */
@Composable
internal fun TodayStatsCard(
    gauge: GaugeUi.Ready,
    modifier: Modifier = Modifier,
) {
    V5Card(modifier.fillMaxWidth().testTag(HOME_STATS_CARD_TAG), radius = 28.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(averageLine(gauge), style = StayFocusedTheme.type.label, color = StayFocusedTheme.colors.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(R.string.home_stat_launches, gauge.launches, Modifier.weight(1f))
                Stat(R.string.home_stat_unlocks, gauge.unlocks, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun averageLine(gauge: GaugeUi.Ready): String {
    val avg = gauge.avgMins ?: return stringResource(R.string.home_gauge_no_avg)
    val delta = gauge.totalMins - avg
    return when {
        delta > 0 -> stringResource(R.string.home_gauge_over, formatMinutes(delta), formatMinutes(avg))
        delta < 0 -> stringResource(R.string.home_gauge_under, formatMinutes(-delta), formatMinutes(avg))
        else -> stringResource(R.string.home_gauge_on_avg, formatMinutes(avg))
    }
}

@Composable
private fun Stat(
    label: Int,
    value: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            value.toString(),
            style = StayFocusedTheme.type.rule.copy(fontSize = 24.sp),
            color = StayFocusedTheme.colors.ink,
        )
        Text(
            stringResource(label),
            style = StayFocusedTheme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = StayFocusedTheme.colors.textSecondary,
        )
    }
}

/** This phone's blocks with their on/off switches and the "Manage" link. */
@Composable
internal fun ActiveBlocksCard(
    blocks: List<HomeBlockUi>,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    V5Card(modifier.fillMaxWidth(), radius = 28.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_blocks_title),
                    style = StayFocusedTheme.type.label.copy(fontSize = 13.sp),
                    color = StayFocusedTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.home_blocks_manage),
                    style = StayFocusedTheme.type.label.copy(textDecoration = TextDecoration.Underline),
                    color = StayFocusedTheme.colors.ink,
                    modifier =
                        Modifier
                            .testTag(HOME_MANAGE_TAG)
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(role = Role.Button, onClick = onManage)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = 8.dp),
                )
            }
            if (blocks.isEmpty()) {
                Column(Modifier.testTag(HOME_BLOCKS_EMPTY_TAG), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.home_blocks_empty_title),
                        style = StayFocusedTheme.type.bodyL,
                        color = StayFocusedTheme.colors.ink,
                    )
                    Text(
                        stringResource(R.string.home_blocks_empty_body),
                        style = StayFocusedTheme.type.body,
                        color = StayFocusedTheme.colors.textSecondary,
                    )
                }
            } else {
                blocks.forEach { row -> BlockRow(row, onToggle) }
            }
        }
    }
}

@Composable
private fun BlockRow(
    row: HomeBlockUi,
    onToggle: (id: String, enabled: Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.block.name,
                style = StayFocusedTheme.type.label.copy(fontSize = 15.sp),
                color = StayFocusedTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                blockDetail(row.block, row.appLabels),
                style = StayFocusedTheme.type.body.copy(fontSize = 13.sp),
                color = StayFocusedTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        V5Toggle(
            checked = row.block.enabled,
            onCheckedChange = { onToggle(row.block.id, it) },
            modifier = Modifier.semantics { contentDescription = row.block.name },
        )
    }
}
