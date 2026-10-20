// Design tokens are literal v5 spec values; the CompositionLocal is the design-system entry point.
@file:Suppress("MagicNumber", "ktlint:compose:compositionlocal-allowlist")

package com.dculus.stayfocused.core.ui.v5.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colour roles from docs/design/V5_SPEC.md §3. Light surfaces; the gradient stops live in [V5Gradients]. */
@Immutable
data class V5Colors(
    val background: Color = Color(0xFFEDEDEB),
    val surface: Color = Color(0xFFFFFFFF),
    val surfaceRaised: Color = Color(0xFFF8F8F6),
    val ink: Color = Color(0xFF000B1A),
    val textSecondary: Color = Color(0xFF5B6472),
    val textTertiary: Color = Color(0xFF3A4250),
    val onInkMuted: Color = Color(0xFFC9CDD3),
    val hairline: Color = Color(0xFFD3D4D6),
    val track: Color = Color(0xFFECECEA),
    val orange: Color = Color(0xFFFE5E0E),
    val orangeGlow: Color = Color(0xFFFF9A3C),
    val orangeGlowDeep: Color = Color(0xFFFF7A2E),
    val orangeSoft: Color = Color(0xFFF7934F),
    val orangeGold: Color = Color(0xFFFEBE42),
    /** Top and middle stops of the current-bar gradient (`#FFB27A → #FE7A2E → orange`). */
    val orangeBarTop: Color = Color(0xFFFFB27A),
    val orangeBarMid: Color = Color(0xFFFE7A2E),
    val violet: Color = Color(0xFF4B3FD1),
    val violetGlow: Color = Color(0xFF9B8CFF),
    val violetTint: Color = Color(0xFFE4DFFF),
    val violetBorder: Color = Color(0xFFE3DFFA),
    val pink: Color = Color(0xFFE8336F),
    val pinkGlow: Color = Color(0xFFEC5A8A),
    val pinkGlowLight: Color = Color(0xFFF07FA2),
    val pinkMid: Color = Color(0xFFEA4D80),
    val pinkSoft: Color = Color(0xFFE26490),
    val pinkTint: Color = Color(0xFFEC6A93),
    val pinkDeep: Color = Color(0xFFD42563),
    val pinkRose: Color = Color(0xFFE77A9E),
    val pinkScan: Color = Color(0xFFF27BA1),
    val pinkText: Color = Color(0xFFC21F59),
    val green: Color = Color(0xFF2F9A3C),
    val greenGlow: Color = Color(0xFF9CCB4A),
    val success: Color = Color(0xFF2F9E45),
    val online: Color = Color(0xFF34C759),
    val onlineBackground: Color = Color(0xFFE8F5EA),
    val onlineText: Color = Color(0xFF1F7A33),
    val red: Color = Color(0xFFE5242B),
    val error: Color = Color(0xFFD12A2A),
    val destructive: Color = Color(0xFFC21F26),
    val lime: Color = Color(0xFFD8F23C),
    val scanBackground: Color = Color(0xFF0D0F12),
    val inkGlow: Color = Color(0xFF2C3A50),
    val google: Color = Color(0xFF4285F4),
    /** Text on a gradient or ink surface. */
    val onGradient: Color = Color(0xFFFFFFFF),
)

/** Launcher-tile fallback colours (letter tile) from the prototype's `APPS[].c`. */
@Immutable
data class V5AppBrandColors(
    val instagram: Color = Color(0xFFE1306C),
    val tiktok: Color = Color(0xFF111318),
    val youtube: Color = Color(0xFFFF0033),
    val snapchat: Color = Color(0xFFFFD400),
    val reddit: Color = Color(0xFFFF4500),
    val x: Color = Color(0xFF000B1A),
    val whatsapp: Color = Color(0xFF22A55A),
    val netflix: Color = Color(0xFFB20710),
    val games: Color = Color(0xFF6B4FE0),
)

internal val LocalV5Colors = staticCompositionLocalOf { V5Colors() }
