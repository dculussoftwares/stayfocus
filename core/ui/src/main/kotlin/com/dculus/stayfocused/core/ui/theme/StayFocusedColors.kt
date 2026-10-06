// Design tokens are literal handoff values; the CompositionLocal is the design-system entry point.
@file:Suppress("MagicNumber", "ktlint:compose:compositionlocal-allowlist")

package com.dculus.stayfocused.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colour roles from the design handoff, "Design tokens (v2)". Dark only. */
@Immutable
data class StayFocusedColors(
    val background: Color = Color(0xFF0E0F0D),
    val panel: Color = Color(0xFF171915),
    val panelGradientStart: Color = Color(0xFF1A1C17),
    val panelGradientEnd: Color = Color(0xFF141611),
    val track: Color = Color(0xFF262922),
    val hairline06: Color = Color(0x0FFFFFFF),
    val hairline08: Color = Color(0x14FFFFFF),
    val hairline10: Color = Color(0x1AFFFFFF),
    val hairline14: Color = Color(0x24FFFFFF),
    val text: Color = Color(0xFFF2F4EC),
    val secondary: Color = Color(0xFFA6AB9D),
    val tertiary: Color = Color(0xFF6E7367),
    val accent: Color = Color(0xFFC6F432),
    val accentHover: Color = Color(0xFFD6FF55),
    val alert: Color = Color(0xFFFF5A47),
    val alertText: Color = Color(0xFFFF7A66),
    val warning: Color = Color(0xFFFFB547),
    val appTints: List<Color> =
        listOf(
            Color(0xFFFF9A8B),
            Color(0xFFFF7A66),
            Color(0xFFFFB547),
            Color(0xFF7EE0A1),
            Color(0xFF9DC3FF),
            Color(0xFFD9DCD2),
            Color(0xFFB9A7FF),
        ),
) {
    /** Text/icon colour on top of [accent] (the lime button). */
    val onAccent: Color get() = background
}

internal val LocalStayFocusedColors = staticCompositionLocalOf { StayFocusedColors() }
