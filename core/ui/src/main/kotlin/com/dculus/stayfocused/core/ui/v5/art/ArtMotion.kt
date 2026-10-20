package com.dculus.stayfocused.core.ui.v5.art

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/** CSS `ease-in-out` (`cubic-bezier(.42, 0, .58, 1)`), the easing of the blink and the scan line. */
internal val EaseInOutCss: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
