@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.charts

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.v5.theme.V5Colors
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import kotlin.math.min

private val CHART_HEIGHT = 130.dp
private const val TRACK_ALPHA = 0.14f
private const val TRACK_BORDER_ALPHA = 0.12f
private const val LOW_TOP_ALPHA = 0.55f
private const val LOW_BOTTOM_ALPHA = 0.35f
private const val AVERAGE_LINE_ALPHA = 0.5f
private const val AXIS_ALPHA = 0.8f
private const val HIGHLIGHT_TOP_ALPHA = 0.6f
private const val LOW_HIGHLIGHT_ALPHA = 0.5f
private const val GLOW_ALPHA = 0.55f
private const val GLOW_BLUR_DP = 14f

/**
 * Glass bar chart of the Insights hero (violet card). One bar per entry of [values] in a translucent track, bar height
 * `value / max` (day: at least 8 % when non-zero), clamped to the track. Bar fills: the [highlightIndex] bar is orange
 * with an orange glow (clipped by the track, as in the prototype), the others white → violet tint when "high" (week: all; day: above 12 min/h) or translucent white when low.
 * A dashed line marks [average] (same unit as [values]) and an "Avg [averageLabel]" pill sits above the top-right
 * corner of the chart (drawn 62 dp above the bars, over the card's headline row). [axisLabels] are spread evenly below.
 *
 * [contentDescription] is the caller's TalkBack summary (e.g. "Screen time by hour, peak 19 min"); the pill and
 * the axis labels are folded into it.
 */
@Composable
fun GlassBarChart(
    values: List<Int>,
    max: Int,
    average: Float,
    averageLabel: String,
    axisLabels: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    mode: GlassBarMode = GlassBarMode.Day,
) {
    val colors = V5Theme.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    role = Role.Image
                },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
                drawBars(values, max, highlightIndex, mode, colors)
                drawAverageLine(averageLineFraction(average, max), colors.onGradient)
            }
            AveragePill(averageLabel, Modifier.align(Alignment.TopEnd).offset(y = (-62).dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            axisLabels.forEach {
                Text(
                    it,
                    style = V5Theme.type.body.copy(fontSize = 12.sp),
                    color = colors.onGradient.copy(alpha = AXIS_ALPHA),
                )
            }
        }
    }
}

@Composable
private fun AveragePill(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = V5Theme.colors
    Surface(
        modifier = modifier.height(24.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.onGradient.copy(alpha = 0.92f),
        contentColor = colors.ink,
        border = BorderStroke(1.dp, colors.violetBorder),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.v5_chart_avg), style = V5Theme.type.tag)
            Text(label, style = V5Theme.type.tag)
        }
    }
}

private fun DrawScope.drawBars(
    values: List<Int>,
    max: Int,
    highlightIndex: Int?,
    mode: GlassBarMode,
    colors: V5Colors,
) {
    val count = values.size
    if (count == 0) return
    val gap = mode.gapDp.dp.toPx()
    val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(0f)
    val radius = min(mode.radiusDp.dp.toPx(), barWidth / 2f)
    val corner = CornerRadius(radius)
    val trackFill = colors.onGradient.copy(alpha = TRACK_ALPHA)
    val trackBorder = colors.onGradient.copy(alpha = TRACK_BORDER_ALPHA)
    values.forEachIndexed { index, value ->
        val left = index * (barWidth + gap)
        drawRoundRect(trackFill, Offset(left, 0f), Size(barWidth, size.height), corner)
        drawRoundRect(
            trackBorder,
            Offset(left + 0.5.dp.toPx(), 0.5.dp.toPx()),
            Size(barWidth - 1.dp.toPx(), size.height - 1.dp.toPx()),
            corner,
            style = Stroke(1.dp.toPx()),
        )
        val fill = barFraction(value, max, mode)
        if (fill <= 0f) return@forEachIndexed
        val top = size.height * (1f - fill)
        val clip = Path().apply { addRoundRect(RoundRect(left, 0f, left + barWidth, size.height, corner)) }
        clipPath(clip) {
            drawBarFill(barStyle(index, value, highlightIndex, mode), left, top, barWidth, corner, colors)
        }
    }
}

/**
 * The prototype's `box-shadow: 0 0 16px rgba(orange, .55)` on the current bar: a blurred copy of the bar behind it.
 * Like the prototype it is clipped to the track by the caller. Blur mask filters are ignored by the platform on API
 * 26–27, where only the gradient and top rim remain.
 */
private fun DrawScope.drawGlow(
    left: Float,
    top: Float,
    width: Float,
    corner: CornerRadius,
    color: Color,
) {
    drawIntoCanvas { canvas ->
        val paint =
            Paint().apply {
                asFrameworkPaint().apply {
                    isAntiAlias = true
                    this.color = color.copy(alpha = GLOW_ALPHA).toArgb()
                    maskFilter = BlurMaskFilter(GLOW_BLUR_DP.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                }
            }
        canvas.drawRoundRect(left, top, left + width, size.height, corner.x, corner.y, paint)
    }
}

private fun DrawScope.drawBarFill(
    style: BarStyle,
    left: Float,
    top: Float,
    width: Float,
    corner: CornerRadius,
    colors: V5Colors,
) {
    val height = size.height - top
    if (style == BarStyle.Current) drawGlow(left, top, width, corner, colors.orange)
    val brush =
        when (style) {
            BarStyle.Current -> {
                Brush.verticalGradient(
                    0f to colors.orangeBarTop,
                    0.45f to colors.orangeBarMid,
                    1f to colors.orange,
                    startY = top,
                    endY = size.height,
                )
            }

            BarStyle.High -> {
                Brush.verticalGradient(listOf(colors.onGradient, colors.violetTint), startY = top, endY = size.height)
            }

            BarStyle.Low -> {
                Brush.verticalGradient(
                    listOf(
                        colors.onGradient.copy(alpha = LOW_TOP_ALPHA),
                        colors.onGradient.copy(alpha = LOW_BOTTOM_ALPHA),
                    ),
                    startY = top,
                    endY = size.height,
                )
            }
        }
    drawRoundRect(brush, Offset(left, top), Size(width, height), corner)
    val highlight =
        when (style) {
            BarStyle.Current -> colors.onGradient.copy(alpha = HIGHLIGHT_TOP_ALPHA)
            BarStyle.High -> colors.onGradient
            BarStyle.Low -> colors.onGradient.copy(alpha = LOW_HIGHLIGHT_ALPHA)
        }
    val band = if (style == BarStyle.Low) 1.dp.toPx() else 1.5.dp.toPx()
    clipPath(Path().apply { addRoundRect(RoundRect(left, top, left + width, size.height, corner)) }) {
        drawRect(highlight, Offset(left, top), Size(width, band))
    }
}

private fun DrawScope.drawAverageLine(
    fraction: Float,
    color: Color,
) {
    val y = size.height * (1f - fraction)
    val stroke = 1.5.dp.toPx()
    val dash = 3.dp.toPx()
    drawLine(
        color = color.copy(alpha = AVERAGE_LINE_ALPHA),
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = stroke,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
    )
}
