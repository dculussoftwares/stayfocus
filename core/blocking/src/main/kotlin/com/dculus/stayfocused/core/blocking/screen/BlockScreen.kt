@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.blocking.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.blocking.R
import com.dculus.stayfocused.core.blocking.evaluator.BlockReason
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.icon.AppIcon
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

/** Status-bar colour of the block screen (prototype "Kids: Blocked overlay"). */
const val BLOCK_STATUS_BAR_COLOR: Int = 0xFF2A120E.toInt()

private val gradientTop = Color(0xFF3A1712)

@StringRes
fun reasonRes(reason: BlockReason): Int =
    when (reason) {
        BlockReason.BREAK -> R.string.block_reason_break
        BlockReason.FOCUS -> R.string.block_reason_focus
        BlockReason.NOW -> R.string.block_reason_now
        BlockReason.LIMIT_DAILY -> R.string.block_reason_limit_daily
        BlockReason.LIMIT_HOURLY -> R.string.block_reason_limit_hourly
        BlockReason.CYCLE_REST -> R.string.block_reason_cycle_rest
        BlockReason.SCHEDULE -> R.string.block_reason_schedule
        BlockReason.MANUAL_LOCK -> R.string.block_reason_manual_lock
    }

/** The block screen body. State comes in, "go home" goes out; [extraActions] is empty in `:app`. */
@Composable
fun BlockScreen(
    pkg: String,
    appLabel: String?,
    reason: BlockReason,
    timeLine: BlockTimeLine,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
    extraActions: @Composable () -> Unit = {},
) {
    val colors = StayFocusedTheme.colors
    val name = appLabel?.takeIf { it.isNotBlank() } ?: pkg.substringAfterLast('.')
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(gradientTop, colors.background), radius = 1400f))
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(120.dp)
                        .border(2.dp, colors.alert.copy(alpha = 0.35f), CircleShape)
                        .padding(14.dp)
                        .clip(CircleShape)
                        .background(colors.alert.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(pkg = pkg, label = appLabel, size = 56.dp)
            }
            Text(
                text = stringResource(R.string.block_locked_label, name.uppercase()),
                style = StayFocusedTheme.type.label,
                color = colors.alertText,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(reasonRes(reason)),
                style = StayFocusedTheme.type.displayM,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            TimeLine(timeLine)
        }
        PrimaryButton(text = stringResource(R.string.block_go_home), onClick = onGoHome)
        extraActions()
    }
}

@Composable
private fun TimeLine(timeLine: BlockTimeLine) {
    val colors = StayFocusedTheme.colors
    when (timeLine) {
        BlockTimeLine.None -> {
            Unit
        }

        is BlockTimeLine.Countdown -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.block_opens_in),
                    style = StayFocusedTheme.type.label,
                    color = colors.secondary,
                )
                Text(timeLine.text, style = StayFocusedTheme.type.numeric.copy(fontSize = 34.sp), color = colors.text)
            }
        }

        is BlockTimeLine.Until -> {
            Text(
                stringResource(R.string.block_until, timeLine.clock),
                style = StayFocusedTheme.type.label,
                color = colors.secondary,
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
internal fun BlockScreenPreview() {
    StayFocusedTheme {
        BlockScreen(
            pkg = "com.instagram.android",
            appLabel = "Instagram",
            reason = BlockReason.LIMIT_DAILY,
            timeLine = BlockTimeLine.Countdown("05:12:09"),
            onGoHome = {},
        )
    }
}
