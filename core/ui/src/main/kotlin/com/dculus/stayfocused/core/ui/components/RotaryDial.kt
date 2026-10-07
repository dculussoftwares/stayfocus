@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.DialConfig
import com.dculus.stayfocused.core.model.DialConfigs
import com.dculus.stayfocused.core.model.DialUnit
import com.dculus.stayfocused.core.model.dialCenterLabel
import com.dculus.stayfocused.core.model.dialLabel
import com.dculus.stayfocused.core.model.dialUnit
import com.dculus.stayfocused.core.model.dialValueForAngle
import com.dculus.stayfocused.core.model.durationLabel
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val DialSize = 240.dp
private const val VIEWBOX = 240f
private const val TICK_COUNT = 60
private const val TICK_STEP_DEG = 6
private const val MAJOR_EVERY = 5
private val TickOff = Color(0xFF33372F)
private val DialFace = Color(0xFF121410)

private fun pointOnCircle(
    cx: Float,
    cy: Float,
    r: Float,
    angleDeg: Float,
): Offset {
    val rad = (angleDeg - 90f) * PI.toFloat() / 180f
    return Offset(cx + r * cos(rad), cy + r * sin(rad))
}

private fun centerFontSize(label: String) =
    when {
        label.length <= 2 -> 54.sp
        label.length == 3 -> 44.sp
        else -> 36.sp
    }

/**
 * The signature rotary dial (prototype `dial`): 240 dp, 60 ticks, progress ring with a knob. Drag or tap
 * anywhere to set [value] (snapped to `config.step`, never below one step); a haptic tick fires on each change.
 * TalkBack gets a range slider with `setProgress`.
 */
@Composable
fun RotaryDial(
    value: Int,
    onValueChange: (Int) -> Unit,
    config: DialConfig,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentConfig by rememberUpdatedState(config)
    val haptics = LocalHapticFeedback.current
    val label = dialCenterLabel(value)
    val unitRes =
        when (dialUnit(value)) {
            DialUnit.MINUTES -> R.string.sf_dial_minutes
            DialUnit.HOUR -> R.string.sf_dial_hour
            DialUnit.HOURS -> R.string.sf_dial_hours
        }
    val steps = ((config.max - config.step) / config.step - 1).coerceAtLeast(0)
    val description = durationLabel(value)
    val fontSize = centerFontSize(label)

    Box(
        modifier =
            modifier
                .size(DialSize)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        fun apply(change: PointerInputChange) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            var angle =
                                Math.toDegrees(
                                    atan2(change.position.x - cx, -(change.position.y - cy)).toDouble(),
                                )
                            if (angle < 0) angle += 360.0
                            val v = dialValueForAngle(angle, currentConfig)
                            if (v != currentValue) {
                                currentOnChange(v)
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                            change.consume()
                        }
                        val down = awaitFirstDown(requireUnconsumed = false)
                        apply(down)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            event.changes.firstOrNull { it.id == down.id }?.let { if (it.pressed) apply(it) }
                        } while (event.changes.any { it.id == down.id && it.pressed })
                    }
                }.clearAndSetSemantics {
                    stateDescription = description
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(value.toFloat(), config.step.toFloat()..config.max.toFloat(), steps)
                    setProgress { target ->
                        val snapped =
                            ((target / config.step).roundToInt() * config.step).coerceIn(config.step, config.max)
                        if (snapped != currentValue) {
                            currentOnChange(snapped)
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                        true
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(DialSize)) {
            drawDial(value, config, c.accent, c.track, c.background, c.hairline06)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = StayFocusedTheme.type.displayXL.copy(fontSize = fontSize, lineHeight = fontSize),
                color = c.text,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = stringResource(unitRes),
                style = StayFocusedTheme.type.label,
                color = c.secondary,
            )
        }
    }
}

private fun DrawScope.drawDial(
    value: Int,
    config: DialConfig,
    lime: Color,
    track: Color,
    knobRing: Color,
    faceStroke: Color,
) {
    val k = size.width / VIEWBOX
    val cx = VIEWBOX / 2f
    val frac = (value.toFloat() / config.max).coerceIn(0f, 1f)
    val sweep = frac * 360f

    drawCircle(DialFace, radius = 108f * k, center = center)
    drawCircle(faceStroke, radius = 108f * k, center = center, style = Stroke(width = 1f * k))
    drawCircle(track, radius = 100f * k, center = center, style = Stroke(width = 8f * k))
    drawArc(
        color = lime,
        startAngle = -90f,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset((cx - 100f) * k, (cx - 100f) * k),
        size = Size(200f * k, 200f * k),
        style = Stroke(width = 8f * k, cap = StrokeCap.Round),
    )
    for (i in 0 until TICK_COUNT) {
        val a = (i * TICK_STEP_DEG).toFloat()
        val major = i % MAJOR_EVERY == 0
        val p1 = pointOnCircle(cx, cx, if (major) 76f else 82f, a)
        val p2 = pointOnCircle(cx, cx, 88f, a)
        drawLine(
            color = if (a <= sweep) lime else TickOff,
            start = Offset(p1.x * k, p1.y * k),
            end = Offset(p2.x * k, p2.y * k),
            strokeWidth = (if (major) 2.4f else 1.3f) * k,
            cap = StrokeCap.Round,
        )
    }
    val knob = pointOnCircle(cx, cx, 100f, sweep)
    val knobCenter = Offset(knob.x * k, knob.y * k)
    drawCircle(knobRing, radius = 15f * k, center = knobCenter)
    drawCircle(lime, radius = 11f * k, center = knobCenter)
}

/** Preset buttons under the dial; the one equal to [value] is selected. */
@Composable
fun DialPresets(
    config: DialConfig,
    value: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        config.presets.forEach { preset ->
            SelectChip(
                label = dialLabel(preset),
                selected = value == preset,
                onClick = {
                    if (preset != value) {
                        onPick(preset)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                },
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 620)
@Composable
internal fun RotaryDialPreview() {
    StayFocusedTheme {
        Column(
            modifier = Modifier.background(StayFocusedTheme.colors.background).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RotaryDial(value = 30, onValueChange = {}, config = DialConfigs.Break)
            DialPresets(DialConfigs.Break, value = 30, onPick = {})
        }
    }
}
