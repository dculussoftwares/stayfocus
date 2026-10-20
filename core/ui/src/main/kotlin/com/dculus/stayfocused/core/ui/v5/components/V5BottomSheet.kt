@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.dculus.stayfocused.core.ui.v5.theme.V5Easing
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import com.dculus.stayfocused.core.ui.v5.theme.V5_SHEET_DURATION_MS
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val V5_SHEET_SCRIM_TAG = "v5_sheet_scrim"
const val V5_SHEET_PANEL_TAG = "v5_sheet_panel"

private const val SCRIM_ALPHA = 0.4f
private const val DISMISS_DRAG_FRACTION = 0.3f
private const val DISMISS_FLING_VELOCITY = 1200f
private const val SNAP_BACK_MILLIS = 200
private val HandleWidth = 40.dp
private val HandleHeight = 5.dp
private val SheetTopPadding = 12.dp
private val SheetHorizontalPadding = 18.dp
private val SheetBottomPadding = 34.dp

/**
 * Whether a drag-down that ended at [offsetPx] (of a sheet [heightPx] tall) with a downward [velocityPxPerSec]
 * dismisses the sheet: dragged past 30% of its height, or flung down fast. Anything else snaps back.
 */
internal fun shouldDismissSheetDrag(
    offsetPx: Float,
    heightPx: Float,
    velocityPxPerSec: Float,
): Boolean = offsetPx > heightPx * DISMISS_DRAG_FRACTION || (offsetPx > 0f && velocityPxPerSec > DISMISS_FLING_VELOCITY)

/**
 * v5 modal bottom sheet: slides up in 300 ms (`cubic-bezier(.2,.8,.2,1)`) over an ink scrim at 40%. Top radius 40,
 * `surfaceRaised`, a 40 × 5 hairline handle, padding 12 / 18 / 34 + the navigation-bar inset. A scrim tap, system back
 * and a drag down all call [onDismiss] (once, even if several arrive during the exit animation); the sheet stays in
 * composition until its exit animation has finished.
 *
 * @param contentSpacing gap between the handle and the content blocks (14 dp; the accessibility sheet uses 12 dp).
 */
@Composable
fun V5BottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentSpacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (state.currentState || state.targetState) {
        // Ignore taps/back while the exit animation runs so the caller is notified once.
        val dismissOnce = { if (state.targetState) onDismiss() }
        Dialog(
            onDismissRequest = dismissOnce,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            // The custom scrim is the only dimming; drop the dialog window's own dim.
            (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
            V5SheetLayout(state, dismissOnce, contentSpacing, modifier, content)
        }
    }
}

@Composable
private fun V5SheetLayout(
    state: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit,
    contentSpacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = V5Theme.colors
    val slide = tween<IntOffset>(V5_SHEET_DURATION_MS, easing = V5Easing)
    val fade = tween<Float>(V5_SHEET_DURATION_MS, easing = V5Easing)
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(visibleState = state, enter = fadeIn(fade), exit = fadeOut(fade)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(V5_SHEET_SCRIM_TAG)
                    .background(colors.ink.copy(alpha = SCRIM_ALPHA))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visibleState = state,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(slide) { it },
            exit = slideOutVertically(slide) { it },
        ) {
            DraggablePanel(onDismiss, contentSpacing, content)
        }
    }
}

@Composable
private fun DraggablePanel(
    onDismiss: () -> Unit,
    contentSpacing: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = V5Theme.colors
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var heightPx by remember { mutableIntStateOf(0) }
    val dragState =
        rememberDraggableState { delta ->
            // Only downwards: the sheet never lifts off its resting position.
            scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f)) }
        }
    Column(
        Modifier
            .testTag(V5_SHEET_PANEL_TAG)
            .fillMaxWidth()
            .onSizeChanged { heightPx = it.height }
            .offset { IntOffset(0, dragOffset.value.roundToInt()) }
            .clip(V5Theme.shapes.sheet)
            .background(colors.surfaceRaised)
            // Consume taps on the panel so they never reach the scrim (no click semantics).
            .pointerInput(Unit) { detectTapGestures { } }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (shouldDismissSheetDrag(dragOffset.value, heightPx.toFloat(), velocity)) {
                        onDismiss()
                    } else {
                        dragOffset.animateTo(0f, tween(SNAP_BACK_MILLIS, easing = V5Easing))
                    }
                },
            ).semantics {
                dismiss {
                    onDismiss()
                    true
                }
            }.navigationBarsPadding()
            .padding(start = SheetHorizontalPadding, end = SheetHorizontalPadding, top = SheetTopPadding)
            .padding(bottom = SheetBottomPadding),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
    ) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        content()
    }
}

@Composable
private fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(HandleWidth, HandleHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(V5Theme.colors.hairline),
    )
}

@Preview(widthDp = 390, heightDp = 420)
@Composable
internal fun V5BottomSheetPreview() {
    V5Theme {
        Box(Modifier.fillMaxSize().background(V5Theme.colors.background)) {
            Box(Modifier.fillMaxSize().background(V5Theme.colors.ink.copy(alpha = SCRIM_ALPHA)))
            V5SheetSample(Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Static sheet panel as in the "Take a break" sheet header, used by previews and screenshot tests. */
@Composable
internal fun V5SheetSample(modifier: Modifier = Modifier) {
    val colors = V5Theme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(V5Theme.shapes.sheet)
            .background(colors.surfaceRaised)
            .padding(start = SheetHorizontalPadding, end = SheetHorizontalPadding, top = SheetTopPadding)
            .padding(bottom = SheetBottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        Text("Sheet title", style = V5Theme.type.heading, color = colors.ink)
        Text(
            "Every distracting app locks. Calls and messages still work.",
            style = V5Theme.type.body,
            color = colors.textSecondary,
        )
    }
}
