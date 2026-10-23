@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.art.BreakArt
import com.dculus.stayfocused.core.ui.components.ActionTile
import com.dculus.stayfocused.core.ui.components.AppIconTile
import com.dculus.stayfocused.core.ui.components.DotMatrixText
import com.dculus.stayfocused.core.ui.icons.V5Icons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients

internal const val HOME_NEW_BLOCK_TAG = "home_new_block"
internal const val HOME_AI_DESCRIBE_TAG = "home_ai_describe"
internal const val BREAK_IDLE_CARD_TAG = "home_break_idle_card"
internal const val BREAK_ACTIVE_CARD_TAG = "home_break_active_card"
internal const val BREAK_COUNTDOWN_TAG = "home_break_countdown"

/** "Block apps" (raised) next to "Take a break" (orange glow). */
@Composable
internal fun HomeTiles(
    state: HomeUiState,
    breakUi: () -> BreakUi,
    breakLengthMins: Int,
    onNewBlock: () -> Unit,
    onOpenBreakSheet: () -> Unit,
    onEndBreak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BlockAppsTile(state.activeBlockCount, state.tiltedApps, onNewBlock, Modifier.weight(1f))
        BreakTile(breakUi, breakLengthMins, onOpenBreakSheet, onEndBreak, Modifier.weight(1f))
    }
}

@Composable
private fun BlockAppsTile(
    activeBlocks: Int,
    apps: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionTile(
        title = stringResource(R.string.home_block_apps_title),
        onClick = onClick,
        modifier = modifier.testTag(HOME_NEW_BLOCK_TAG),
        contentDescription = stringResource(R.string.home_block_apps_description, activeBlocks),
    ) {
        // Tilted tiles: the later app sits higher and to the right, like the prototype.
        apps.getOrNull(0)?.let { pkg ->
            TiltedApp(pkg, -12f, Modifier.align(Alignment.BottomEnd).padding(end = 58.dp, bottom = 80.dp))
        }
        apps.getOrNull(1)?.let { pkg ->
            TiltedApp(pkg, 6f, Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 90.dp))
        }
        ActiveBlocksFooter(activeBlocks)
    }
}

@Composable
private fun TiltedApp(
    pkg: String,
    degrees: Float,
    modifier: Modifier = Modifier,
) {
    AppIconTile(pkg, 50.dp, modifier.rotate(degrees).shadow(8.dp, RoundedCornerShape(17.dp)))
}

@Composable
private fun BoxScope.ActiveBlocksFooter(count: Int) {
    val colors = StayFocusedTheme.colors
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(8.dp)
            .fillMaxWidth()
            .height(62.dp)
            .shadow(6.dp, RoundedCornerShape(28.dp), clip = false)
            .background(colors.surface, RoundedCornerShape(28.dp))
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(R.string.home_active_blocks),
            style = StayFocusedTheme.type.label.copy(fontSize = 15.sp),
            color = colors.ink,
        )
        Text(
            stringResource(R.string.home_active_blocks_count, count),
            style = StayFocusedTheme.type.body.copy(fontSize = 13.sp),
            color = colors.textSecondary,
        )
    }
}

/**
 * The break tile. Only the idle/running switch is read here; the per-second countdown is read inside
 * [BreakCountdown], so the rest of Home does not recompose while the break runs.
 */
@Composable
private fun BreakTile(
    breakUi: () -> BreakUi,
    lengthMins: Int,
    onOpenSheet: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running by remember(breakUi) { derivedStateOf { breakUi() is BreakUi.Active } }
    if (running) {
        ActionTile(
            title = stringResource(R.string.break_tile_running_title),
            onClick = onEnd,
            modifier = modifier.testTag(BREAK_ACTIVE_CARD_TAG),
            gradient = V5Gradients.breakTile,
            showPlus = false,
        ) {
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BreakCountdown(breakUi)
                Text(
                    stringResource(R.string.break_tile_end_early),
                    style = StayFocusedTheme.type.label.copy(fontSize = 13.sp),
                    color = StayFocusedTheme.colors.onGradient,
                )
            }
        }
    } else {
        ActionTile(
            title = stringResource(R.string.break_tile_idle_title),
            onClick = onOpenSheet,
            modifier = modifier.testTag(BREAK_IDLE_CARD_TAG),
            gradient = V5Gradients.breakTile,
            contentDescription = stringResource(R.string.break_tile_idle_description),
        ) {
            BreakArt(lengthMins, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).fillMaxWidth())
        }
    }
}

/** The mm:ss numerals: the only node that reads the countdown state each second. */
@Composable
private fun BreakCountdown(breakUi: () -> BreakUi) {
    val text = (breakUi() as? BreakUi.Active)?.let { paddedMmss(it.remainingMs) }.orEmpty()
    DotMatrixText(
        text = text,
        contentDescription = text,
        dotSize = 5.dp,
        color = StayFocusedTheme.colors.onGradient,
        modifier = Modifier.testTag(BREAK_COUNTDOWN_TAG),
    )
}

/** White pill that opens "Describe with AI". */
@Composable
internal fun AiPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val shape = RoundedCornerShape(31.dp)
    val label = stringResource(R.string.home_ai_pill)
    Row(
        modifier
            .testTag(HOME_AI_DESCRIBE_TAG)
            .fillMaxWidth()
            .height(62.dp)
            .shadow(
                10.dp,
                shape,
                clip = false,
                ambientColor = colors.ink.copy(alpha = 0.4f),
                spotColor = colors.ink.copy(alpha = 0.4f),
            ).clip(shape)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(start = 18.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(V5Icons.sparkleTwin, contentDescription = null, tint = colors.orange, modifier = Modifier.size(20.dp))
        Text(
            label,
            style = StayFocusedTheme.type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(46.dp).background(colors.ink, CircleShape), contentAlignment = Alignment.Center) {
            Icon(V5Icons.arrow, contentDescription = null, tint = colors.onGradient, modifier = Modifier.size(18.dp))
        }
    }
}
