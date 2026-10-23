@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Easing
import com.dculus.stayfocused.core.ui.theme.v5NavigationBarPadding

const val V5_TOAST_TAG = "v5_toast"

private const val TOAST_FADE_IN_MILLIS = 250
private const val TOAST_FADE_OUT_MILLIS = 150
private val ToastMaxWidth = 320.dp
private val ToastRadius = 24.dp

/**
 * Bottom-aligned host for [controller]'s toast (one at a time, 2.6 s, see `ToastController`). Place it last in a
 * full-size `Box` over the screen. The toast sits [bottomOffset] (96 dp) above the navigation-bar inset, which clears
 * the floating tab bar.
 */
@Composable
fun V5ToastHost(
    controller: ToastController,
    modifier: Modifier = Modifier,
    bottomOffset: Dp = 96.dp,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = controller.message,
            modifier = Modifier.align(Alignment.BottomCenter).v5NavigationBarPadding(extra = bottomOffset),
            transitionSpec = {
                fadeIn(tween(TOAST_FADE_IN_MILLIS, easing = V5Easing))
                    .togetherWith(fadeOut(tween(TOAST_FADE_OUT_MILLIS, easing = V5Easing)))
            },
            label = "v5Toast",
        ) { text ->
            if (text != null) V5Toast(text)
        }
    }
}

/** The toast pill: ink background, white 15/500 centred text, r24, padding 14 × 20, at most 320 dp wide. */
@Composable
fun V5Toast(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val shape = RoundedCornerShape(ToastRadius)
    Text(
        text = text,
        modifier =
            modifier
                // Side margin first, then the 320 dp cap on the pill itself (the tag/semantics node is the pill).
                .padding(horizontal = 16.dp)
                .widthIn(max = ToastMaxWidth)
                .testTag(V5_TOAST_TAG)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .shadow(
                    16.dp,
                    shape,
                    ambientColor = colors.ink.copy(alpha = 0.35f),
                    spotColor = colors.ink.copy(alpha = 0.35f),
                ).background(colors.ink, shape)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        style =
            StayFocusedTheme.type.body.copy(
                color = colors.surface,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
    )
}

@Preview(widthDp = 390, heightDp = 160)
@Composable
internal fun V5ToastPreview() {
    StayFocusedTheme {
        Box(
            Modifier.fillMaxSize().background(StayFocusedTheme.colors.background),
            contentAlignment = Alignment.Center,
        ) {
            V5Toast("Break started: 30 min")
        }
    }
}
