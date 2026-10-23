@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.RulerScale
import com.dculus.stayfocused.core.model.RulerTick
import com.dculus.stayfocused.core.model.v5Duration
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

private val RULER_HEIGHT = 72.dp
private val CURRENT_HEIGHT = 70.dp
private val PASSED_HEIGHT = 46.dp
private val AHEAD_HEIGHT = 26.dp
private val CURRENT_WIDTH = 4.dp
private val OTHER_WIDTH = 2.dp
private val TICK_RADIUS = 2.dp
private const val PASSED_ALPHA = 0.95f
private const val AHEAD_ALPHA = 0.5f

/** Colour of the ruler's ticks per state (prototype `ruler(…, onC, offC, curC)`); alpha is applied by the ruler. */
@Immutable
data class TickRulerColors(
    val passed: Color,
    val ahead: Color,
    val current: Color,
) {
    companion object {
        /** Every tick white: the rulers on the gradient cards of the new-block wizard. */
        @Composable
        fun onGradient(): TickRulerColors {
            val c = StayFocusedTheme.colors.onGradient
            return TickRulerColors(passed = c, ahead = c, current = c)
        }

        /** Break sheet: passed orange, ahead `#C9CBCF`, current ink. */
        @Composable
        fun breakSheet(): TickRulerColors =
            TickRulerColors(
                passed = StayFocusedTheme.colors.orange,
                ahead = Color(0xFFC9CBCF),
                current = StayFocusedTheme.colors.ink,
            )
    }
}

/**
 * The v5 duration slider (V5_SPEC §4 `TickRuler`): 45 bottom-aligned ticks across the width, 72 dp tall. The current
 * tick is 4 × 70 dp, passed ticks 2 × 46 dp (α .95), ahead ticks 2 × 26 dp (α .5). Tap or drag anywhere to set the
 * value (`pointer x / width`, snapped by [scale]); a haptic tick fires when the value changes.
 *
 * Accessibility: exposes slider semantics (`progressBarRangeInfo` with discrete steps and `setProgress`) so TalkBack
 * can adjust it without dragging; the arrow and volume keys move one step. [contentDescription] names the control
 * and [valueDescription] is the spoken value (e.g. "45 minutes"); the caller supplies both.
 */
@Composable
fun TickRuler(
    value: Int,
    onValueChange: (Int) -> Unit,
    scale: RulerScale,
    colors: TickRulerColors,
    contentDescription: String,
    valueDescription: String,
    modifier: Modifier = Modifier,
) {
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentScale by rememberUpdatedState(scale)
    val haptics = LocalHapticFeedback.current
    val ticks = remember(value, scale) { scale.ticks(value) }

    // Last value this ruler asked for; key repeats can arrive before the parent recomposes with the new value.
    val lastRequested = remember { intArrayOf(value) }
    SideEffect { lastRequested[0] = value }

    fun emit(next: Int) {
        if (next == lastRequested[0]) return
        lastRequested[0] = next
        currentOnChange(next)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(RULER_HEIGHT)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        fun apply(change: PointerInputChange) {
                            val fraction = if (size.width > 0) change.position.x.toDouble() / size.width else 0.0
                            emit(currentScale.valueAt(fraction))
                            change.consume()
                        }
                        val down = awaitFirstDown(requireUnconsumed = false)
                        apply(down)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            event.changes.firstOrNull { it.id == down.id }?.let { if (it.pressed) apply(it) }
                        } while (event.changes.any { it.id == down.id && it.pressed })
                    }
                }.onKeyEvent { event ->
                    val delta = keyStepOf(event.type, event.key)
                    if (delta != 0) emit(currentScale.stepBy(lastRequested[0], delta))
                    delta != 0
                }.focusable()
                .clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    stateDescription = valueDescription
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            current = value.toFloat(),
                            range = scale.min.toFloat()..scale.max.toFloat(),
                            steps = (scale.stepCount - 1).coerceAtLeast(0),
                        )
                    setProgress { target ->
                        emit(currentScale.snap(target.toDouble()))
                        true
                    }
                },
    ) { drawRuler(ticks, colors) }
}

/** +1 / −1 step for the keys that adjust a slider (arrows, volume), 0 for everything else. */
internal fun keyStepOf(
    type: KeyEventType,
    key: Key,
): Int {
    if (type != KeyEventType.KeyDown) return 0
    return when (key) {
        Key.DirectionRight, Key.DirectionUp, Key.VolumeUp -> 1
        Key.DirectionLeft, Key.DirectionDown, Key.VolumeDown -> -1
        else -> 0
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRuler(
    ticks: List<RulerTick>,
    colors: TickRulerColors,
) {
    val count = ticks.size
    if (count == 0) return
    val currentW = CURRENT_WIDTH.toPx()
    val otherW = OTHER_WIDTH.toPx()
    val totalW = ticks.sumOf { (if (it == RulerTick.Current) currentW else otherW).toDouble() }.toFloat()
    // CSS `justify-content: space-between`: equal gaps between the ticks, none at the edges.
    val gap = if (count > 1) (size.width - totalW) / (count - 1) else 0f
    val radius = CornerRadius(TICK_RADIUS.toPx())
    var x = 0f
    for (tick in ticks) {
        val w = if (tick == RulerTick.Current) currentW else otherW
        val h =
            when (tick) {
                RulerTick.Current -> CURRENT_HEIGHT
                RulerTick.Passed -> PASSED_HEIGHT
                RulerTick.Ahead -> AHEAD_HEIGHT
            }.toPx()
        val color =
            when (tick) {
                RulerTick.Current -> colors.current
                RulerTick.Passed -> colors.passed.copy(alpha = colors.passed.alpha * PASSED_ALPHA)
                RulerTick.Ahead -> colors.ahead.copy(alpha = colors.ahead.alpha * AHEAD_ALPHA)
            }
        drawRoundRect(color, Offset(x, size.height - h), Size(w, h), radius)
        x += w + gap
    }
}

/** "{min}", "Drag to set", "{max}" row under a [TickRuler]; [min] and [max] are minutes. */
@Composable
fun RulerLabels(
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    color: Color = StayFocusedTheme.colors.onGradient,
) {
    val style = StayFocusedTheme.type.label.copy(fontSize = 13.sp)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(v5Duration(min), style = style, color = color)
        Text(stringResource(R.string.v5_ruler_drag_to_set), style = style, color = color)
        Text(v5Duration(max), style = style, color = color)
    }
}
