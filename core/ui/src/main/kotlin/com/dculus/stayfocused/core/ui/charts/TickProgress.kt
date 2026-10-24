@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

private val TICK_WIDTH = 2.dp
private val TICK_RADIUS = 1.dp
private val LIT_HEIGHT = 30.dp
private val UNLIT_HEIGHT = 14.dp

/** Draws bottom-aligned vertical ticks of the given [heights] spread edge to edge (CSS `space-between`). */
internal fun DrawScope.drawTicks(
    heights: List<Dp>,
    colors: List<Color>,
) {
    val count = heights.size
    if (count == 0) return
    val width = TICK_WIDTH.toPx()
    val step = if (count > 1) (size.width - width) / (count - 1) else 0f
    val radius = CornerRadius(TICK_RADIUS.toPx())
    for (i in 0 until count) {
        val h = heights[i].toPx()
        drawRoundRect(colors[i], Offset(i * step, size.height - h), Size(width, h), radius)
    }
}

/**
 * Usage-against-limit meter of a block card: 40 two-dp ticks across the width, the first `fraction × 40` lit
 * (30 dp, green), the rest unlit (14 dp, hairline). [fraction] is `used / limit`; it is clamped to 0..1 and NaN counts
 * as 0. [contentDescription] is the caller's TalkBack summary (e.g. "1 h 12 min used of 2 h").
 */
@Composable
fun TickProgress(
    fraction: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val heights =
        List(TICK_PROGRESS_COUNT) { if (isProgressTickLit(it, fraction)) LIT_HEIGHT else UNLIT_HEIGHT }
    val tickColors =
        List(TICK_PROGRESS_COUNT) { if (isProgressTickLit(it, fraction)) colors.green else colors.hairline }
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(LIT_HEIGHT)
                .clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    role = Role.Image
                },
    ) { drawTicks(heights, tickColors) }
}
