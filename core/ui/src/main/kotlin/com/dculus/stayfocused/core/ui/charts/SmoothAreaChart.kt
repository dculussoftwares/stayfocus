@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

private val CHART_HEIGHT = AREA_CHART_HEIGHT_DP.dp
private val STROKE_WIDTH = 2.4.dp
private val DOT_RADIUS = 5.dp
private val HALO_WIDTH = 5.dp
private const val FILL_ALPHA = 0.55f
private const val HALO_ALPHA = 0.28f
private const val AXIS_ALPHA = 0.85f

/**
 * v6 area chart (replaces [DotGrid] and [GlassBarChart]): [values] are evenly spaced and joined by cubic Béziers whose
 * control points sit at the horizontal midpoint between neighbours. White 2.4 dp stroke with round caps and joins, a
 * fill fading from white 55 % to 0 and, at [highlightIndex], a 10 dp dot with a 5 dp halo (white 28 %). [axisLabels]
 * are spread evenly underneath at 11 sp semibold, 85 % opacity.
 *
 * [contentDescription] is the caller's TalkBack summary (e.g. "Screen time by hour, peak 19 min at 9 PM").
 */
@Composable
fun SmoothAreaChart(
    values: List<Int>,
    max: Int,
    axisLabels: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    color: Color = StayFocusedTheme.colors.onGradient,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    role = Role.Image
                },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            drawArea(values, max, highlightIndex, color)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            axisLabels.forEach {
                Text(
                    it,
                    style = StayFocusedTheme.type.label.copy(fontSize = 11.sp),
                    color = color.copy(alpha = AXIS_ALPHA),
                )
            }
        }
    }
}

private fun DrawScope.drawArea(
    values: List<Int>,
    max: Int,
    highlightIndex: Int?,
    color: Color,
) {
    if (values.isEmpty()) return
    val points =
        values.mapIndexed { i, v ->
            Offset(areaChartX(i, values.size, size.width), areaChartY(v, max).dp.toPx())
        }
    val line = Path().apply { smoothThrough(points) }
    val area =
        Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    val fill = listOf(color.copy(alpha = FILL_ALPHA), color.copy(alpha = 0f))
    drawPath(area, Brush.verticalGradient(fill, startY = 0f, endY = size.height))
    drawPath(line, color, style = Stroke(STROKE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    highlightIndex?.let { points.getOrNull(it) }?.let { p ->
        drawCircle(color.copy(alpha = HALO_ALPHA), DOT_RADIUS.toPx() + HALO_WIDTH.toPx(), p)
        drawCircle(color, DOT_RADIUS.toPx(), p)
    }
}

/** `M p0`, then `C(mx, y0) (mx, y1) (x1, y1)` per segment, `mx` being the midpoint of the two x positions. */
private fun Path.smoothThrough(points: List<Offset>) {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val (x0, y0) = points[i - 1]
        val (x1, y1) = points[i]
        val mx = (x0 + x1) / 2f
        cubicTo(mx, y0, mx, y1, x1, y1)
    }
}
