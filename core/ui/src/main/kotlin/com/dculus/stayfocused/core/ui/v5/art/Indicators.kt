@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import com.dculus.stayfocused.core.ui.v5.theme.rememberInfiniteMotion

private const val SPINNER_MILLIS = 800
private const val RING_ALPHA = 0.4f
private const val ARC_SWEEP = 90f

/**
 * 18 dp loading ring: a 2 dp ring at .4 alpha with a full-alpha arc on top, one turn per .8 s (`sfSpin`, white in the
 * prototype). Static when animations are off. Decorative: hidden from TalkBack, so the control around it says what is
 * loading.
 */
@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    val angle by rememberInfiniteMotion(from = 0f, to = 360f, durationMillis = SPINNER_MILLIS)
    Canvas(modifier.clearAndSetSemantics { }.wrapContentSize().size(18.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2f
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        val topLeft = Offset(inset, inset)
        drawArc(
            color.copy(alpha = RING_ALPHA),
            0f,
            360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        rotate(angle) {
            drawArc(color, -90f, ARC_SWEEP, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        }
    }
}

private const val PULSE_MILLIS = 1800
private const val PULSE_START_SCALE = 0.7f
private const val PULSE_END_SCALE = 1.8f
private const val PULSE_START_ALPHA = 0.9f
private val PulseDotSize = 8.dp

/**
 * 8 dp dot with an expanding, fading ring (`sfPulse`: scale .7 → 1.8, opacity .9 → 0, 1.8 s). With `pulse = false`, or
 * when animations are off, only the dot shows (the ring rests fully faded). Decorative: hidden from TalkBack (the
 * online/offline label says it).
 */
@Composable
fun PulseDot(
    color: Color,
    modifier: Modifier = Modifier,
    pulse: Boolean = true,
) {
    Box(modifier.clearAndSetSemantics { }.wrapContentSize().size(PulseDotSize)) {
        if (pulse) PulseRing(color)
        Canvas(Modifier.size(PulseDotSize)) { drawCircle(color) }
    }
}

/** The expanding ring. Rests at the end of the cycle (fully faded) when animations are off. */
@Composable
private fun PulseRing(color: Color) {
    val progress by rememberInfiniteMotion(from = 0f, to = 1f, durationMillis = PULSE_MILLIS, staticValue = 1f)
    Canvas(
        Modifier
            .size(PulseDotSize)
            .graphicsLayer {
                val scale = PULSE_START_SCALE + (PULSE_END_SCALE - PULSE_START_SCALE) * progress
                scaleX = scale
                scaleY = scale
                alpha = PULSE_START_ALPHA * (1f - progress)
            },
    ) { drawCircle(color) }
}

@Preview(widthDp = 200, heightDp = 60)
@Composable
internal fun IndicatorsPreview() {
    V5Theme {
        Row(
            Modifier.background(V5Theme.colors.ink).padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spinner()
            PulseDot(V5Theme.colors.online)
            PulseDot(V5Theme.colors.lime, pulse = false)
        }
    }
}
