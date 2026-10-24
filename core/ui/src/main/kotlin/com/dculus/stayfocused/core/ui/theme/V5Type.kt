// Design tokens are literal v5 spec values; the CompositionLocal is the design-system entry point.
@file:Suppress("MagicNumber", "ktlint:compose:compositionlocal-allowlist")

package com.dculus.stayfocused.core.ui.theme

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

@OptIn(ExperimentalTextApi::class)
private fun plusJakartaSans(weight: Int) =
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

/** Plus Jakarta Sans (SIL OFL 1.1), bundled variable font (`wght` 200-800); the weights the design uses. */
val PlusJakartaSansFontFamily: FontFamily = FontFamily(listOf(200, 300, 400, 500, 600, 700, 800).map(::plusJakartaSans))

/** Type scale of V5_SPEC §3. Where the spec gives a range (34–36, 20–22, …) the lower bound is the base style. */
@Immutable
data class V5Type(
    /** 92 / 200: wizard ruler value (88 in the break sheet via `.copy(fontSize = 88.sp)`). */
    val displayXL: TextStyle,
    /** 66 / 200: Insights total, schedule times (58). */
    val displayL: TextStyle,
    /** 62 / 300: "Take back your time." */
    val hero: TextStyle,
    /** 38 / 300: tab titles. */
    val title: TextStyle,
    /** 34 / 300: onboarding and linking headlines. */
    val headline: TextStyle,
    /** 28 / 400: wizard step title, sheet titles. */
    val heading: TextStyle,
    /** 20 / 500: "Block apps", child name. */
    val cardTitle: TextStyle,
    /** 22 / 300: block rule sentence, kid stats. */
    val rule: TextStyle,
    /** 17 / 600: buttons, list titles. */
    val bodyL: TextStyle,
    /** 15 / 400. */
    val body: TextStyle,
    /** 14 / 600: captions, chips, tab labels. */
    val label: TextStyle,
    /** 12 / 700: tags. */
    val tag: TextStyle,
)

fun v5Type(family: FontFamily = PlusJakartaSansFontFamily): V5Type {
    fun style(
        size: Int,
        weight: Int,
        lineHeight: Float,
        tracking: Float = 0f,
    ) = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(weight),
        fontSize = size.sp,
        lineHeight = (size * lineHeight).sp,
        letterSpacing = tracking.em,
    )
    return V5Type(
        displayXL = style(92, 200, 0.95f, -0.04f),
        displayL = style(66, 200, 1f, -0.03f),
        hero = style(62, 300, 0.98f, -0.025f),
        title = style(38, 300, 1.1f, -0.02f),
        headline = style(34, 300, 1.05f, -0.02f),
        heading = style(28, 400, 1.15f),
        cardTitle = style(20, 500, 1.12f),
        rule = style(22, 300, 1.2f),
        bodyL = style(17, 600, 1.3f),
        body = style(15, 400, 1.4f),
        label = style(14, 600, 1.3f),
        tag = style(12, 700, 1.2f),
    )
}

internal val LocalV5Type = staticCompositionLocalOf { v5Type() }
