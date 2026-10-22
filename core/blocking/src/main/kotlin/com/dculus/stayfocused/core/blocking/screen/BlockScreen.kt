@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.blocking.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.blocking.R
import com.dculus.stayfocused.core.blocking.evaluator.BlockReason
import com.dculus.stayfocused.core.ui.v5.components.AppIconTile
import com.dculus.stayfocused.core.ui.v5.components.DotMatrixText
import com.dculus.stayfocused.core.ui.v5.components.PillButton
import com.dculus.stayfocused.core.ui.v5.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.v5.theme.V5Gradients
import com.dculus.stayfocused.core.ui.v5.theme.V5Screen
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import com.dculus.stayfocused.core.ui.v5.theme.grainOverlay
import com.dculus.stayfocused.core.ui.v5.theme.v5NavigationBarPadding
import com.dculus.stayfocused.core.ui.v5.theme.v5StatusBarPadding

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
        BlockReason.ALL_LOCKED -> R.string.block_reason_all_locked
    }

/** Minutes of a cycle rule ("use, then lock"), for the rule line of a rest block. */
data class CycleRuleMins(
    val use: Int,
    val rest: Int,
)

/**
 * Countdown text as the v5 dot-matrix shows it: `mm:ss`, or `h:mm:ss` from one hour. [BlockTimeLine.Countdown] carries
 * `m:ss` / `hh:mm:ss` (shipped format), so only padding changes here.
 */
fun dotCountdownText(text: String): String {
    val parts = text.split(':')
    return when (parts.size) {
        2 -> "${parts[0].padStart(2, '0')}:${parts[1]}"
        3 -> "${parts[0].toIntOrNull() ?: 0}:${parts[1]}:${parts[2]}"
        else -> text
    }
}

private val gradientGrain = Modifier.fillMaxSize().grainOverlay()

/** The block screen body. State comes in, "go home" goes out; [extraActions] is empty in `:app`. */
@Composable
fun BlockScreen(
    pkg: String,
    appLabel: String?,
    reason: BlockReason,
    timeLine: BlockTimeLine,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
    cycleRule: CycleRuleMins? = null,
    extraActions: @Composable () -> Unit = {},
) {
    val name = appLabel?.takeIf { it.isNotBlank() } ?: pkg.substringAfterLast('.')
    V5Screen(darkContent = true, modifier = modifier, background = V5Gradients.blocked.adaptive) {
        Box(gradientGrain)
        val colors = V5Theme.colors
        val type = V5Theme.type
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .v5StatusBarPadding(extra = 50.dp)
                    .v5NavigationBarPadding(extra = 40.dp)
                    .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIconTile(
                pkg = pkg,
                size = 76.dp,
                label = appLabel,
                modifier = Modifier.shadow(16.dp, RoundedCornerShape(26.dp), clip = false, spotColor = colors.ink),
            )
            Text(
                text = stringResource(R.string.block_resting, name),
                style = type.headline,
                color = colors.onGradient,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 22.dp),
            )
            TimeLine(timeLine)
            val ruleText =
                if (reason == BlockReason.CYCLE_REST && cycleRule != null) {
                    stringResource(R.string.block_cycle_rule, cycleRule.use, cycleRule.rest, cycleRule.use)
                } else {
                    stringResource(reasonRes(reason))
                }
            Text(
                text = ruleText,
                style = type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.onGradient,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp).padding(top = 26.dp),
            )
            Spacer(Modifier.weight(1f))
            PillButton(
                text = stringResource(R.string.block_close_app, name),
                onClick = onGoHome,
                variant = PillButtonVariant.White,
                modifier = Modifier.fillMaxWidth(),
            )
            extraActions()
        }
    }
}

@Composable
private fun TimeLine(timeLine: BlockTimeLine) {
    val colors = V5Theme.colors
    when (timeLine) {
        BlockTimeLine.None -> {
            Unit
        }

        is BlockTimeLine.Countdown -> {
            val shown = dotCountdownText(timeLine.text)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.block_opens_again_in),
                    style = V5Theme.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onGradient,
                    modifier = Modifier.padding(top = 6.dp),
                )
                DotMatrixText(
                    text = shown,
                    contentDescription = shown,
                    dotSize = if (shown.length > 5) 6.dp else 9.dp,
                    color = colors.onGradient,
                    modifier = Modifier.padding(top = 22.dp),
                )
            }
        }

        is BlockTimeLine.Until -> {
            Text(
                stringResource(R.string.block_until, timeLine.clock),
                style = V5Theme.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onGradient,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
internal fun BlockScreenPreview() {
    BlockScreen(
        pkg = "com.instagram.android",
        appLabel = "Instagram",
        reason = BlockReason.CYCLE_REST,
        timeLine = BlockTimeLine.Countdown("14:52"),
        onGoHome = {},
        cycleRule = CycleRuleMins(use = 10, rest = 30),
    )
}
