// Design tokens are literal v5 spec values; the CompositionLocal is the design-system entry point.
@file:Suppress("MagicNumber", "ktlint:compose:compositionlocal-allowlist")

package com.dculus.stayfocused.core.ui.v5.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Screen paddings, gaps and fixed heights of V5_SPEC §3. */
@Immutable
data class V5Spacing(
    val screenTabs: Dp = 14.dp,
    val screenOnboarding: Dp = 18.dp,
    val screenKids: Dp = 22.dp,
    val gap4: Dp = 4.dp,
    val gap6: Dp = 6.dp,
    val gap8: Dp = 8.dp,
    val gap10: Dp = 10.dp,
    val gap12: Dp = 12.dp,
    val gap14: Dp = 14.dp,
    val gap16: Dp = 16.dp,
    val gap18: Dp = 18.dp,
    val gap22: Dp = 22.dp,
    val primaryButtonHeight: Dp = 62.dp,
    val sheetButtonHeight: Dp = 60.dp,
    val secondaryButtonHeight: Dp = 46.dp,
    val inputHeight: Dp = 58.dp,
    val roundButtonSize: Dp = 46.dp,
    val tabHeight: Dp = 50.dp,
    val fabSize: Dp = 58.dp,
    /** Minimum touch target for any control. */
    val minTouchTarget: Dp = 48.dp,
)

internal val LocalV5Spacing = staticCompositionLocalOf { V5Spacing() }
