@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.formatMinutes
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import kotlin.math.sin

private const val GAUGE_W = 300f
private const val CENTRE = 150f
private const val RADIUS = 124f
private const val START_DEG = -120f
private const val SWEEP_DEG = 240f
private const val TICK_COUNT = 41
private const val TICK_STEP_DEG = 6f
private const val MAJOR_TICK_EVERY = 10
private val GaugeWidth = 300.dp
private val GaugeHeight = 176.dp
private val UnlitTick = Color(0xFF33372F)

private fun polar(
    r: Float,
    deg: Float,
    scale: Float,
): Offset {
    val t = Math.toRadians(deg.toDouble())
    return Offset((CENTRE + r * sin(t).toFloat()) * scale, (CENTRE - r * kotlin.math.cos(t).toFloat()) * scale)
}

/**
 * 240° screen-time gauge (-120° to +120°, 0° = up): track, value arc, 41 ticks (major every 10th; ticks up to
 * the value are lit), average marker and a centre readout [content] slot. 300 x 176 dp like the prototype.
 */
@Composable
fun ScreenTimeGauge(
    valueMins: Int,
    avgMins: Int,
    modifier: Modifier = Modifier,
    maxMins: Int = 240,
    content: @Composable () -> Unit = {},
) {
    val c = StayFocusedTheme.colors
    val max = maxMins.coerceAtLeast(1)
    val valueFrac = (valueMins.toFloat() / max).coerceIn(0f, 1f)
    val avgFrac = (avgMins.toFloat() / max).coerceIn(0f, 1f)
    val description = stringResource(R.string.sf_gauge_description, formatMinutes(valueMins), formatMinutes(avgMins))
    Box(
        modifier =
            modifier
                .size(GaugeWidth, GaugeHeight)
                .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Canvas(Modifier.size(GaugeWidth, GaugeHeight).clip(RectangleShape).clearAndSetSemantics { }) {
            val s = size.width / GAUGE_W
            val topLeft = Offset((CENTRE - RADIUS) * s, (CENTRE - RADIUS) * s)
            val arcSize = Size(2 * RADIUS * s, 2 * RADIUS * s)
            val stroke = Stroke(width = 14f * s, cap = StrokeCap.Round)
            // Compose angles start at 3 o'clock; the gauge's 0° is 12 o'clock.
            drawArc(c.track, START_DEG - 90f, SWEEP_DEG, false, topLeft, arcSize, style = stroke)
            if (valueFrac > 0f) {
                drawArc(c.accent, START_DEG - 90f, SWEEP_DEG * valueFrac, false, topLeft, arcSize, style = stroke)
            }
            val litUntil = START_DEG + SWEEP_DEG * valueFrac
            repeat(TICK_COUNT) { i ->
                val a = START_DEG + i * TICK_STEP_DEG
                val major = i % MAJOR_TICK_EVERY == 0
                drawLine(
                    color = if (a <= litUntil) c.accent.copy(alpha = 0.7f) else UnlitTick,
                    start = polar(if (major) 100f else 105f, a, s),
                    end = polar(110f, a, s),
                    strokeWidth = (if (major) 2.2f else 1.2f) * s,
                    cap = StrokeCap.Round,
                )
            }
            val avgA = START_DEG + SWEEP_DEG * avgFrac
            drawLine(c.text, polar(112f, avgA, s), polar(136f, avgA, s), strokeWidth = 3f * s, cap = StrokeCap.Round)
        }
        Box(
            Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 90.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            content()
        }
    }
}

@Preview(widthDp = 360, heightDp = 420)
@Composable
internal fun ScreenTimeGaugePreview() {
    StayFocusedTheme {
        val c = StayFocusedTheme.colors
        Column(Modifier.background(c.background).padding(20.dp)) {
            ScreenTimeGauge(valueMins = 147, avgMins = 139) {
                Text("2h 27m", style = StayFocusedTheme.type.numeric.copy(fontSize = 30.sp), color = c.text)
            }
            ScreenTimeGauge(valueMins = 0, avgMins = 60, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
