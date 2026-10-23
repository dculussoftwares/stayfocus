@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp

/** Prototype grid cell size; everything is scaled from this when the grid fills its width. */
private const val DOT_PITCH = 10f
private const val LIT_RADIUS = 2.2f
private const val UNLIT_RADIUS = 1.4f
private const val LIT_ALPHA = 0.85f
private const val UNLIT_ALPHA = 0.32f

/**
 * Dot-matrix bar chart (Home hero, child hero): one column per entry of [values], [rows] dots per column, the bottom
 * `round(v / max × rows)` of them lit. Values above [max] fill the column; negatives, a zero [max] and an empty list
 * draw unlit dots or nothing. The highlighted column ([highlightIndex]) is fully opaque. The grid keeps the
 * prototype's aspect ratio, so it fills the width and scales the 10-unit pitch and the dot radii (lit 2.2, unlit 1.4).
 *
 * [contentDescription] is the TalkBack summary, supplied by the caller (e.g. "Screen time by hour, peak 19 min").
 */
@Composable
fun DotGrid(
    values: List<Int>,
    max: Int,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    rows: Int = 6,
    highlightIndex: Int? = null,
) {
    val columns = values.size
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (columns > 0 &&
                        rows > 0
                    ) {
                        Modifier.aspectRatio(columns.toFloat() / rows)
                    } else {
                        Modifier.height(0.dp)
                    },
                ).clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    role = Role.Image
                },
    ) {
        if (columns == 0 || rows <= 0) return@Canvas
        val pitch = size.width / columns
        val scale = pitch / DOT_PITCH
        values.forEachIndexed { column, value ->
            val lit = dotGridLitCount(value, max, rows)
            repeat(rows) { row ->
                val on = rows - row <= lit
                drawCircle(
                    color = color,
                    radius = (if (on) LIT_RADIUS else UNLIT_RADIUS) * scale,
                    center = Offset(column * pitch + pitch / 2f, row * pitch + pitch / 2f),
                    alpha = if (on) (if (column == highlightIndex) 1f else LIT_ALPHA) else UNLIT_ALPHA,
                )
            }
        }
    }
}
