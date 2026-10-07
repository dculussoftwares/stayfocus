@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** The handoff easing, `cubic-bezier(.2,.8,.2,1)`. */
val SfEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

const val SF_SHEET_DURATION_MS = 300
const val SF_SCREEN_DURATION_MS = 350
private const val SCREEN_EXIT_DURATION_MS = 150

/** Screen enter: fade + 8 dp translate-Y over 350 ms. Needs a [Density] to convert the 8 dp. */
fun sfScreenEnter(density: Density): EnterTransition {
    val offsetPx = with(density) { 8.dp.roundToPx() }
    return fadeIn(tween(SF_SCREEN_DURATION_MS, easing = SfEasing)) +
        slideInVertically(tween(SF_SCREEN_DURATION_MS, easing = SfEasing)) { offsetPx }
}

/** Screen exit: a short fade, so the incoming screen's enter reads as the only motion. */
fun sfScreenExit(): ExitTransition = fadeOut(tween(SCREEN_EXIT_DURATION_MS, easing = SfEasing))
