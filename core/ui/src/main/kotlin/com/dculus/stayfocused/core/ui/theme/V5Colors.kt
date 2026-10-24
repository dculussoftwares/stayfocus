// Design tokens are literal v5 spec values; the CompositionLocal is the design-system entry point.
@file:Suppress("MagicNumber", "ktlint:compose:compositionlocal-allowlist")

package com.dculus.stayfocused.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colour roles from docs/design/V5_SPEC.md §3. Light surfaces; the gradient stops live in [V5Gradients]. */
@Immutable
data class V5Colors(
    val background: Color = Color(0xFFEDEDEB),
    val surface: Color = Color(0xFFFFFFFF),
    val surfaceRaised: Color = Color(0xFFF8F8F6),
    val ink: Color = Color(0xFF163D22),
    val textSecondary: Color = Color(0xFF56604F),
    val textTertiary: Color = Color(0xFF38432F),
    val onInkMuted: Color = Color(0xFFC5D6BA),
    val hairline: Color = Color(0xFFD3D4D6),
    val track: Color = Color(0xFFECECEA),
    /** v6 accent: icons, ticks, rank bars, FAB, AI draft and Request tags, links; base of [V5Gradients.brand]. */
    val green: Color = Color(0xFF2F9A3C),
    /** [V5Gradients.brand] highlight. */
    val greenGlow: Color = Color(0xFF9CCB4A),
    /** Welcome gradient mid highlight and Blocked overlay. */
    val greenFresh: Color = Color(0xFF5DB548),
    /** Welcome gradient light highlight. */
    val greenLight: Color = Color(0xFFB9DE6A),
    /** Scan line gradient centre. */
    val greenScanLine: Color = Color(0xFFC8E88F),
    /** "Stay Focused Kids" label on the consent screen. */
    val greenLabel: Color = Color(0xFF23772F),
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
