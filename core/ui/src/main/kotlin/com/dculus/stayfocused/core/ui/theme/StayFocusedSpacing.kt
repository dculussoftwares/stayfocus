// Design tokens are literal handoff values; the CompositionLocal is the design-system entry point.
@file:Suppress("ktlint:compose:compositionlocal-allowlist")

package com.dculus.stayfocused.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing from the handoff: screen padding 18; gaps 6 / 8 / 10 / 12 / 14 / 16 / 22. */
@Immutable
data class StayFocusedSpacing(
    val screen: Dp = 18.dp,
    val gap6: Dp = 6.dp,
    val gap8: Dp = 8.dp,
    val gap10: Dp = 10.dp,
    val gap12: Dp = 12.dp,
    val gap14: Dp = 14.dp,
    val gap16: Dp = 16.dp,
    val gap22: Dp = 22.dp,
)

internal val LocalStayFocusedSpacing = staticCompositionLocalOf { StayFocusedSpacing() }
