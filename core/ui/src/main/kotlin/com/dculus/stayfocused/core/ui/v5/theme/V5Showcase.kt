// Showcase sample sizes are layout literals.
@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private fun V5Colors.swatches(): List<Pair<String, Color>> =
    listOf(
        "background" to background,
        "surface" to surface,
        "surfaceRaised" to surfaceRaised,
        "ink" to ink,
        "textSecondary" to textSecondary,
        "textTertiary" to textTertiary,
        "onInkMuted" to onInkMuted,
        "hairline" to hairline,
        "track" to track,
        "orange" to orange,
        "orangeGlow" to orangeGlow,
        "orangeGlowDeep" to orangeGlowDeep,
        "orangeSoft" to orangeSoft,
        "orangeGold" to orangeGold,
        "violet" to violet,
        "violetGlow" to violetGlow,
        "violetTint" to violetTint,
        "violetBorder" to violetBorder,
        "pink" to pink,
        "pinkGlow" to pinkGlow,
        "pinkGlowLight" to pinkGlowLight,
        "pinkMid" to pinkMid,
        "pinkSoft" to pinkSoft,
        "pinkTint" to pinkTint,
        "pinkDeep" to pinkDeep,
        "pinkRose" to pinkRose,
        "pinkScan" to pinkScan,
        "pinkText" to pinkText,
        "green" to green,
        "greenGlow" to greenGlow,
        "success" to success,
        "online" to online,
        "onlineBackground" to onlineBackground,
        "onlineText" to onlineText,
        "red" to red,
        "error" to error,
        "destructive" to destructive,
        "lime" to lime,
        "scanBackground" to scanBackground,
        "inkGlow" to inkGlow,
        "google" to google,
    )

private fun V5Type.styles(): List<Pair<String, TextStyle>> =
    listOf(
        "displayXL 92/200" to displayXL,
        "displayL 66/200" to displayL,
        "hero 62/300" to hero,
        "title 38/300" to title,
        "headline 34/300" to headline,
        "heading 28/400" to heading,
        "cardTitle 20/500" to cardTitle,
        "rule 22/300" to rule,
        "bodyL 17/600" to bodyL,
        "body 15/400" to body,
        "label 14/600" to label,
        "tag 12/700" to tag,
    )

/** Colours, type scale and every gradient with grain, for previews. */
@Composable
fun V5Showcase(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        V5ShowcaseTokens()
        V5ShowcaseGradients()
    }
}

/** Colours, type scale and weights (own Roborazzi baseline). */
@Composable
internal fun V5ShowcaseTokens() {
    val colors = V5Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Colours", style = V5Theme.type.title, color = colors.ink)
        colors.swatches().chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, color) -> Swatch(name, color, Modifier.weight(1f)) }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        Text("Type", style = V5Theme.type.title, color = colors.ink)
        V5Theme.type.styles().forEach { (name, style) ->
            Column {
                Text(name, style = V5Theme.type.tag, color = colors.textSecondary)
                Text("Stay Focused 0123", style = style, color = colors.ink)
            }
        }
        Text("Weights", style = V5Theme.type.title, color = colors.ink)
        listOf(200, 300, 400, 500, 600, 700).forEach { weight ->
            Text(
                "Urbanist $weight, take back your time",
                style = V5Theme.type.heading.copy(fontWeight = FontWeight(weight)),
                color = colors.ink,
            )
        }
    }
}

/** Every named gradient with grain (own Roborazzi baseline, mdpi to stay under the 500 KB file limit). */
@Composable
internal fun V5ShowcaseGradients() {
    val colors = V5Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Gradients", style = V5Theme.type.title, color = colors.ink)
        V5Gradients.all.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (name, gradient) ->
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(110.dp)
                                .glow(gradient, V5Theme.shapes.card),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(
                            name,
                            style = V5Theme.type.label,
                            color = colors.onGradient,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
                repeat(2 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Swatch(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(color, RectangleShape)
                .border(1.dp, V5Theme.colors.hairline),
        )
        Text(name, style = V5Theme.type.tag, color = V5Theme.colors.ink)
        Text(
            "#%06X".format(color.toArgb() and 0xFFFFFF),
            style = V5Theme.type.tag,
            color = V5Theme.colors.textSecondary,
        )
    }
}

@Preview(widthDp = 390, heightDp = 1800)
@Composable
private fun V5ShowcasePreview() {
    V5Screen(darkContent = false) { V5Showcase() }
}
