// Design tokens are literal v5 spec values; the CompositionLocal is the design-system entry point.
@file:Suppress("MagicNumber", "ktlint:compose:compositionlocal-allowlist")

package com.dculus.stayfocused.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Radii of V5_SPEC §3. Buttons and chips are full pills ([pill]); day chips and round buttons are circles. */
@Immutable
data class V5Shapes(
    val tag: RoundedCornerShape = RoundedCornerShape(14.dp),
    val input: RoundedCornerShape = RoundedCornerShape(24.dp),
    val segmented: RoundedCornerShape = RoundedCornerShape(28.dp),
    val card: RoundedCornerShape = RoundedCornerShape(28.dp),
    val cardLarge: RoundedCornerShape = RoundedCornerShape(36.dp),
    val hero: RoundedCornerShape = RoundedCornerShape(38.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
    val tabBar: RoundedCornerShape = RoundedCornerShape(32.dp),
    val appTileSmall: RoundedCornerShape = RoundedCornerShape(13.dp),
    val appTile: RoundedCornerShape = RoundedCornerShape(16.dp),
    val appTileLarge: RoundedCornerShape = RoundedCornerShape(26.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
)

internal val LocalV5Shapes = staticCompositionLocalOf { V5Shapes() }
