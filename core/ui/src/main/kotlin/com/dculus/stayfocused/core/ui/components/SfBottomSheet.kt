@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dculus.stayfocused.core.ui.theme.SF_SHEET_DURATION_MS
import com.dculus.stayfocused.core.ui.theme.SfEasing
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

const val SF_SHEET_SCRIM_TAG = "sf_sheet_scrim"
const val SF_SHEET_PANEL_TAG = "sf_sheet_panel"

/**
 * Modal bottom sheet: slides up in 300 ms with [SfEasing], 60% black scrim, closes on a scrim tap
 * and on system back. Top corners r32, panel background, drag handle.
 *
 * The sheet stays in composition until its exit animation has finished.
 */
@Composable
fun SfBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (state.currentState || state.targetState) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            SfSheetLayout(state, onDismiss, modifier, content)
        }
    }
}

@Composable
private fun SfSheetLayout(
    state: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = StayFocusedTheme.colors
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = state,
            enter = fadeIn(tween(SF_SHEET_DURATION_MS, easing = SfEasing)),
            exit = fadeOut(tween(SF_SHEET_DURATION_MS, easing = SfEasing)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(SF_SHEET_SCRIM_TAG)
                    .background(Color.Black.copy(alpha = 0.6f))
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
            enter = slideInVertically(tween(SF_SHEET_DURATION_MS, easing = SfEasing)) { it },
            exit = slideOutVertically(tween(SF_SHEET_DURATION_MS, easing = SfEasing)) { it },
        ) {
            val shape = StayFocusedTheme.shapes.sheet
            Column(
                Modifier
                    .testTag(SF_SHEET_PANEL_TAG)
                    .fillMaxWidth()
                    .clip(shape.topOnly())
                    .background(colors.panel)
                    // Swallow taps on the panel so they never reach the scrim.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ).navigationBarsPadding()
                    .padding(horizontal = StayFocusedTheme.spacing.gap16, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .padding(bottom = 12.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.hairline14),
                )
                content()
            }
        }
    }
}

private fun RoundedCornerShape.topOnly(): RoundedCornerShape =
    copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)

@Preview(widthDp = 360, heightDp = 400)
@Composable
internal fun SfBottomSheetPreview() {
    StayFocusedTheme {
        Box(Modifier.fillMaxSize().background(StayFocusedTheme.colors.background)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(StayFocusedTheme.shapes.sheet.topOnly())
                    .background(StayFocusedTheme.colors.panel)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .padding(bottom = 12.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(StayFocusedTheme.colors.hairline14),
                )
                MonoLabel("SHEET CONTENT")
            }
        }
    }
}
