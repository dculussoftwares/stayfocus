@file:Suppress("MagicNumber", "ktlint:compose:compositionlocal-allowlist")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.theme.SfEasing
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val SF_TOAST_DURATION_MS = 2600L
const val SF_TOAST_TAG = "sf_toast"

/** Light toast background (`#F2F4EC`, the handoff text colour) and dark text (`#0E0F0D`). */
private val ToastBackground = Color(0xFFF2F4EC)
private val ToastText = Color(0xFF0E0F0D)

/**
 * Holds the one visible toast. [show] replaces the current message (like the prototype's `toastMsg`)
 * and restarts the 2.6 s auto-dismiss timer. Not thread-safe: call from the main thread.
 */
@Stable
class ToastController(
    private val scope: CoroutineScope,
    private val durationMs: Long = SF_TOAST_DURATION_MS,
) {
    /** The message currently shown, or null. */
    var message: String? by mutableStateOf(null)
        private set

    private var dismissJob: Job? = null

    fun show(message: String) {
        dismissJob?.cancel()
        this.message = message
        dismissJob =
            scope.launch {
                delay(durationMs)
                this@ToastController.message = null
            }
    }

    fun dismiss() {
        dismissJob?.cancel()
        message = null
    }
}

/** Provide a [ToastController] to the tree so any screen can call `LocalToastController.current.show(..)`. */
val LocalToastController =
    compositionLocalOf<ToastController> { error("No ToastController provided; wrap in SfToastHost's owner.") }

/** Bottom-aligned host for [controller]'s toast. Place it last in a full-size `Box`. */
@Composable
fun SfToastHost(
    controller: ToastController,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 104.dp,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = controller.message,
            modifier = Modifier.align(Alignment.BottomCenter),
            transitionSpec = {
                (fadeIn(tween(250, easing = SfEasing)) + slideInVertically(tween(250, easing = SfEasing)) { it / 4 })
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "toast",
        ) { text ->
            if (text != null) {
                SfToast(text, Modifier.padding(start = 16.dp, end = 16.dp, bottom = bottomPadding))
            }
        }
    }
}

@Composable
internal fun SfToast(
    text: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .testTag(SF_TOAST_TAG)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .fillMaxWidth()
            .shadow(24.dp, shape)
            .background(ToastBackground, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(ToastText, CircleShape))
        Text(
            text,
            color = ToastText,
            style = StayFocusedTheme.type.body.copy(fontWeight = FontWeight.Bold, color = ToastText),
        )
    }
}

@Preview(widthDp = 360, heightDp = 200)
@Composable
internal fun SfToastPreview() {
    StayFocusedTheme {
        Box(Modifier.fillMaxSize().background(StayFocusedTheme.colors.background).padding(vertical = 18.dp)) {
            SfToast("Instagram locked on Aarav's phone", Modifier.align(Alignment.Center).padding(horizontal = 16.dp))
        }
    }
}
