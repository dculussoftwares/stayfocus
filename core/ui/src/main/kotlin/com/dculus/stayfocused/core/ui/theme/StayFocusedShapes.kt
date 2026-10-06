// Design tokens are literal handoff values; the CompositionLocal is the design-system entry point.
@file:Suppress("ktlint:compose:compositionlocal-allowlist")

package com.dculus.stayfocused.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Corner radii from the handoff: chips 8, inputs/buttons 16, cards 20, hero 28, sheets 32, tab bar 24. */
@Immutable
data class StayFocusedShapes(
    val chip: RoundedCornerShape = RoundedCornerShape(8.dp),
    val input: RoundedCornerShape = RoundedCornerShape(16.dp),
    val button: RoundedCornerShape = RoundedCornerShape(16.dp),
    val card: RoundedCornerShape = RoundedCornerShape(20.dp),
    val hero: RoundedCornerShape = RoundedCornerShape(28.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(32.dp),
    val tabBar: RoundedCornerShape = RoundedCornerShape(24.dp),
)

internal val LocalStayFocusedShapes = staticCompositionLocalOf { StayFocusedShapes() }
