@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.model.LedColumn
import com.dculus.stayfocused.core.model.ledLevels
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

private val UnlitCell = Color(0xFF1F221C)
private val CellGap = 3.dp

/**
 * 24-column LED bar chart. [levels] come from `ledLevels`; lit cells use the accent colour, the current hour the
 * text colour, unlit cells `#1F221C`. [avgFraction] (0..1 of the chart height) draws a dashed average line.
 * [axisLabels] are spread evenly under the chart (e.g. 00 / 06 / 12 / 18 / 23). Decorative: pass the data summary
 * in the caller's semantics. [cellHeight] is 5 dp for the mini chart (Home) and 7 dp for Insights.
 */
@Composable
fun LedBarChart(
    levels: List<LedColumn>,
    rows: Int,
    modifier: Modifier = Modifier,
    avgFraction: Float? = null,
    axisLabels: List<String> = emptyList(),
    cellHeight: Dp = 7.dp,
    cellRadius: Dp = 2.dp,
) {
    val c = StayFocusedTheme.colors
    val chartHeight = cellHeight * rows + CellGap * (rows - 1).coerceAtLeast(0)
    Column(modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
            val gap = CellGap.toPx()
            val cellH = cellHeight.toPx()
            val cols = levels.size.coerceAtLeast(1)
            val cellW = (size.width - gap * (cols - 1)) / cols
            levels.forEachIndexed { i, col ->
                val lit = if (col.isCurrent) c.text else c.accent
                repeat(rows) { r ->
                    val y = size.height - (r + 1) * cellH - r * gap
                    drawRoundRect(
                        color = if (r < col.level) lit else UnlitCell,
                        topLeft = Offset(i * (cellW + gap), y),
                        size = Size(cellW, cellH),
                        cornerRadius = CornerRadius(cellRadius.toPx()),
                    )
                }
            }
            if (avgFraction != null) {
                val y = size.height * (1f - avgFraction.coerceIn(0f, 1f))
                drawLine(
                    color = c.text.copy(alpha = 0.55f),
                    start = Offset(-4.dp.toPx(), y),
                    end = Offset(size.width + 4.dp.toPx(), y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
        }
        if (axisLabels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                axisLabels.forEach { Text(it, style = StayFocusedTheme.type.labelS, color = c.tertiary) }
            }
        }
    }
}

private val PrototypeHours = listOf(0, 0, 0, 0, 0, 0, 2, 6, 14, 9, 5, 8, 12, 4, 3, 7, 10, 6, 9, 18, 22, 15, 8, 2)

@Preview(widthDp = 360, heightDp = 360)
@Composable
internal fun LedBarChartPreview() {
    StayFocusedTheme {
        Column(
            Modifier.background(StayFocusedTheme.colors.background).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LedBarChart(ledLevels(PrototypeHours, 5, 10), rows = 5, cellHeight = 5.dp, cellRadius = 1.5.dp)
            LedBarChart(
                levels = ledLevels(PrototypeHours, 10, 10),
                rows = 10,
                avgFraction = 139f / 24f / PrototypeHours.max(),
                axisLabels = listOf("00", "06", "12", "18", "23"),
            )
        }
    }
}
