@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.v5.theme.V5Gradients
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import com.dculus.stayfocused.core.ui.v5.theme.rememberInfiniteMotion
import com.dculus.stayfocused.core.ui.v5.theme.rememberV5AnimationsEnabled

/** Preview/screenshot surface: the v5 background, or the welcome gradient for controls that sit on a gradient. */
@Composable
internal fun V5PreviewSurface(
    gradient: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    V5Theme {
        Column(
            modifier =
                Modifier
                    .background(if (gradient) V5Gradients.welcome.adaptive else SolidColor(V5Theme.colors.background))
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

private const val SPINNER_MILLIS = 800
private const val RING_ALPHA = 0.4f
private const val ARC_SWEEP = 90f

/** 18 dp loading ring: 2 dp ring at .4 alpha with a full-alpha arc on top, one turn per .8 s (`sfSpin`). */
@Composable
internal fun V5Spinner(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val angle by rememberInfiniteMotion(from = 0f, to = 360f, durationMillis = SPINNER_MILLIS)
    Canvas(modifier.size(18.dp)) {
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

/**
 * 8 dp dot with an expanding, fading ring (`sfPulse`: scale .7 → 1.8, opacity .9 → 0, 1.8 s).
 * Static when animations are off.
 */
@Composable
internal fun V5PulseDot(
    color: Color,
    modifier: Modifier = Modifier,
    pulse: Boolean = true,
) {
    val animate = pulse && rememberV5AnimationsEnabled()
    Box(modifier.size(8.dp)) {
        if (animate) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(PULSE_MILLIS, easing = LinearEasing), RepeatMode.Restart),
                label = "pulseProgress",
            )
            Canvas(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        val scale = PULSE_START_SCALE + (PULSE_END_SCALE - PULSE_START_SCALE) * progress
                        scaleX = scale
                        scaleY = scale
                        alpha = PULSE_START_ALPHA * (1f - progress)
                    },
            ) { drawCircle(color) }
        }
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
    }
}
