@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

private const val PULSE_MILLIS = 1800
private const val PULSE_MIN_ALPHA = 0.7f
private const val GLOW_ALPHA = 0.55f
private const val RING_GROWTH = 5f / 8f

/**
 * Status dot. [glow] draws a soft halo; [pulse] loops opacity and an expanding ring every 1.8 s
 * (static in previews and screenshot tests). Decorative: hidden from TalkBack.
 */
@Composable
fun LedDot(
    modifier: Modifier = Modifier,
    color: Color = StayFocusedTheme.colors.accent,
    size: Dp = 8.dp,
    glow: Boolean = false,
    pulse: Boolean = false,
) {
    val animate = pulse && !LocalInspectionMode.current
    val phase =
        if (animate) {
            val t = rememberInfiniteTransition(label = "led")
            t
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(PULSE_MILLIS, easing = LinearEasing), RepeatMode.Restart),
                    label = "ledPhase",
                ).value
        } else {
            0f
        }
    val alphaNow = if (animate) 1f - (1f - PULSE_MIN_ALPHA) * (1f - kotlin.math.abs(2f * phase - 1f)) else 1f
    Box(
        modifier =
            modifier
                .clearAndSetSemantics { }
                .size(size)
                .drawBehind {
                    val r = this.size.minDimension / 2f
                    if (glow) drawCircle(color.copy(alpha = GLOW_ALPHA * 0.5f), radius = r * 2f)
                    if (animate) {
                        drawCircle(color.copy(alpha = GLOW_ALPHA * (1f - phase)), radius = r + r * 5f / 4f * phase)
                    }
                }.alpha(alphaNow)
                .background(color, androidx.compose.foundation.shape.CircleShape),
    )
}

/** Small upper-case mono caption (`label` role). */
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = StayFocusedTheme.colors.secondary,
) {
    Text(text = text.uppercase(), style = StayFocusedTheme.type.label, color = color, modifier = modifier)
}

/** Section title in mono caps with an optional accent action on the right (e.g. "INSIGHTS →"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel(title, modifier = Modifier.semantics(mergeDescendants = true) { })
        if (action != null) {
            MonoLabel(
                text = action,
                color = StayFocusedTheme.colors.accent,
                modifier =
                    Modifier
                        .sizeIn(minHeight = 48.dp)
                        .then(
                            if (onAction !=
                                null
                            ) {
                                Modifier.clickable(role = Role.Button, onClick = onAction)
                            } else {
                                Modifier
                            },
                        ).wrapCenter(),
            )
        }
    }
}

private fun Modifier.wrapCenter(): Modifier = this.padding(vertical = 14.dp)

/** Label + big mono value in a [Panel]. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Panel(modifier.semantics(mergeDescendants = true) { }) {
        MonoLabel(label)
        Text(
            text = value,
            style = StayFocusedTheme.type.numeric.copy(fontSize = 28.sp),
            color = StayFocusedTheme.colors.text,
            modifier = Modifier.padding(top = StayFocusedTheme.spacing.gap6),
        )
    }
}

@Preview(widthDp = 360, heightDp = 260)
@Composable
internal fun IndicatorsPreview() {
    StayFocusedTheme {
        val c = StayFocusedTheme.colors
        Column(
            modifier = Modifier.background(c.background).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LedDot()
                LedDot(glow = true)
                LedDot(glow = true, pulse = true, color = c.alert)
                MonoLabel("Screen time")
            }
            SectionHeader("Screen time", action = "INSIGHTS →", onAction = {})
            StatTile("Today", "1h 52m")
        }
    }
}
