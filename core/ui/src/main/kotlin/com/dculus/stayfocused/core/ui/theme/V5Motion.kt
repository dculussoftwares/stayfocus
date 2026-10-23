@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.theme

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** v5 sheet slide-up duration (V5_SPEC §3 Motion): 300 ms. */
const val V5_SHEET_DURATION_MS: Int = 300

/** v5 screen enter duration: 350 ms. */
const val V5_SCREEN_DURATION_MS: Int = 350

private const val SCREEN_EXIT_DURATION_MS = 150

/** `cubic-bezier(.2,.8,.2,1)`, the easing of sheets, toasts and screen enters. */
val V5Easing: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** Screen enter: fade + 8 dp rise over 350 ms. Needs a [Density] to convert the 8 dp. */
fun v5ScreenEnter(density: Density): EnterTransition {
    val offsetPx = with(density) { 8.dp.roundToPx() }
    return fadeIn(tween(V5_SCREEN_DURATION_MS, easing = V5Easing)) +
        slideInVertically(tween(V5_SCREEN_DURATION_MS, easing = V5Easing)) { offsetPx }
}

/** Screen exit: a short fade, so the incoming screen's enter reads as the only motion. */
fun v5ScreenExit(): ExitTransition = fadeOut(tween(SCREEN_EXIT_DURATION_MS, easing = V5Easing))

/**
 * Pure rule behind [rememberV5AnimationsEnabled]: infinite animations stop in previews and screenshot tests
 * ([inspectionMode]) and when the user turned animations off ("Remove animations", [animatorDurationScale] == 0).
 */
internal fun infiniteMotionEnabled(
    inspectionMode: Boolean,
    animatorDurationScale: Float,
): Boolean = !inspectionMode && animatorDurationScale != 0f

/**
 * Whether infinite animations (spinner, pulse, scan line, ...) may run. Read once per composition from
 * `ANIMATOR_DURATION_SCALE`; a changed setting applies the next time the screen is composed.
 */
@Composable
fun rememberV5AnimationsEnabled(): Boolean {
    val inspection = LocalInspectionMode.current
    val resolver = LocalContext.current.contentResolver
    val scale =
        remember(resolver) {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    return infiniteMotionEnabled(inspection, scale)
}

/**
 * A looping float for the v5 infinite animations (`sfPulse`, `sfSpin`, `sfBlink`, `sfScan`, ...): runs from [from]
 * to [to] in [durationMillis] and repeats.
 *
 * When animations are off ([rememberV5AnimationsEnabled] is false) it returns [staticValue] and starts **no**
 * infinite transition, so nothing keeps the frame clock busy.
 */
@Composable
fun rememberInfiniteMotion(
    from: Float,
    to: Float,
    durationMillis: Int,
    easing: Easing = LinearEasing,
    repeatMode: RepeatMode = RepeatMode.Restart,
    staticValue: Float = from,
): State<Float> {
    if (!rememberV5AnimationsEnabled()) return rememberUpdatedState(staticValue)
    val transition = rememberInfiniteTransition(label = "v5InfiniteMotion")
    return transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = easing), repeatMode),
        label = "v5InfiniteMotionValue",
    )
}
