// Design tokens are literal handoff values; the CompositionLocal is the design-system entry point.
@file:Suppress("MagicNumber", "ktlint:compose:compositionlocal-allowlist")

package com.dculus.stayfocused.core.ui.theme

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.R

/**
 * Font families. Clash Display and Satoshi cannot be redistributed in this repo (see docs/FONTS.md), so
 * the bundled defaults are OFL substitutes (Space Grotesk, Plus Jakarta Sans). [loadFontFamilies] prefers
 * the real fonts when `scripts/dev/fetch-fonts.sh` has placed them in `core/ui/src/main/assets/fonts/`.
 */
@Immutable
data class StayFocusedFontFamilies(
    val display: FontFamily,
    val body: FontFamily,
    val mono: FontFamily,
)

@OptIn(ExperimentalTextApi::class)
private fun variable(
    resId: Int,
    weight: Int,
) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val bundledDisplay = FontFamily(variable(R.font.space_grotesk, 600))
private val bundledBody =
    FontFamily(
        listOf(400, 500, 700, 800).map { variable(R.font.plus_jakarta_sans, it) },
    )
private val bundledMono =
    FontFamily(
        listOf(500, 600, 700).map { variable(R.font.geist_mono, it) },
    )

val BundledFontFamilies = StayFocusedFontFamilies(bundledDisplay, bundledBody, bundledMono)

private val clashFiles = listOf("ClashDisplay-Semibold.otf" to 600)
private val satoshiFiles =
    listOf(
        "Satoshi-Regular.otf" to 400,
        "Satoshi-Medium.otf" to 500,
        "Satoshi-Bold.otf" to 700,
        "Satoshi-Black.otf" to 800,
    )

/** Real Clash Display / Satoshi when all their files are in assets, otherwise the bundled OFL substitutes. */
fun loadFontFamilies(context: Context): StayFocusedFontFamilies {
    val present =
        context.assets
            .list("fonts")
            ?.toSet()
            .orEmpty()

    fun family(
        files: List<Pair<String, Int>>,
        fallback: FontFamily,
    ): FontFamily =
        if (files.all { it.first in present }) {
            FontFamily(
                files.map { (name, weight) ->
                    Font(path = "fonts/$name", assetManager = context.assets, weight = FontWeight(weight))
                },
            )
        } else {
            fallback
        }
    return StayFocusedFontFamilies(
        display = family(clashFiles, bundledDisplay),
        body = family(satoshiFiles, bundledBody),
        mono = bundledMono,
    )
}

/** Named type roles from the handoff. Use `.uppercase()` on the text for [label] and [labelS]. */
@Immutable
data class StayFocusedTypography(
    val displayXL: TextStyle,
    val displayL: TextStyle,
    val displayM: TextStyle,
    val display: TextStyle,
    val displayS: TextStyle,
    val title: TextStyle,
    val bodyL: TextStyle,
    val body: TextStyle,
    val bodyS: TextStyle,
    val label: TextStyle,
    val labelS: TextStyle,
    /** Mono numerals, 16-60 sp: use `numeric.copy(fontSize = ...)`. */
    val numeric: TextStyle,
)

fun stayFocusedTypography(families: StayFocusedFontFamilies = BundledFontFamilies): StayFocusedTypography {
    fun display(
        size: Int,
        spacing: Float,
    ) = TextStyle(
        fontFamily = families.display,
        fontWeight = FontWeight.SemiBold,
        fontSize = size.sp,
        lineHeight = (size * 1.1f).sp,
        letterSpacing = spacing.em,
    )

    fun body(
        size: Float,
        weight: FontWeight,
    ) = TextStyle(
        fontFamily = families.body,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * 1.4f).sp,
        letterSpacing = 0.em,
    )

    fun mono(
        size: Float,
        spacing: Float,
        weight: FontWeight,
    ) = TextStyle(
        fontFamily = families.mono,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * 1.3f).sp,
        letterSpacing = spacing.em,
    )
    return StayFocusedTypography(
        displayXL = display(54, -0.03f),
        displayL = display(44, -0.03f),
        displayM = display(38, -0.025f),
        display = display(34, -0.025f),
        displayS = display(26, -0.02f),
        title = display(22, -0.02f),
        bodyL = body(16f, FontWeight.Medium),
        body = body(14f, FontWeight.Normal),
        bodyS = body(12.5f, FontWeight.Normal),
        label = mono(11f, 0.1f, FontWeight.Medium),
        labelS = mono(10f, 0.1f, FontWeight.Medium),
        numeric = mono(16f, -0.02f, FontWeight.Bold),
    )
}

internal val LocalStayFocusedTypography = staticCompositionLocalOf { stayFocusedTypography() }
