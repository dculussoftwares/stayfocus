@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.UrbanistFontFamily

private const val THIN_FONT_SCALE = 9

/**
 * Key numbers (screen time, counts, countdowns, the 6-digit link code) as 5 × 7 dot glyphs with dot pitch [dotSize]
 * (circles of radius .34 × [dotSize]); or, with [Numerals.Thin], as light Urbanist text at 9 × [dotSize].
 *
 * Intrinsic size is the glyph width × 7 [dotSize]. Digits all have the same advance, so a running countdown keeps a
 * fixed width per glyph and doesn't jitter. Supports 0–9, `:` and space (2 cells); other characters are skipped.
 *
 * The glyphs are hidden from TalkBack: [contentDescription] is what is announced (e.g. "2 hours 33 minutes").
 */
@Composable
fun DotMatrixText(
    text: String,
    contentDescription: String,
    dotSize: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    numerals: Numerals = Numerals.Dots,
) {
    val hidden = modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    when (numerals) {
        Numerals.Dots -> DotGlyphs(text, dotSize, color, hidden)
        Numerals.Thin -> ThinNumerals(text, dotSize, color, hidden)
    }
}

@Composable
private fun DotGlyphs(
    text: String,
    dotSize: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // Laid out in dp units so the intrinsic size is exact; scaled to px when drawing.
    val layout = remember(text, dotSize) { dotMatrixLayout(text, dotSize.value) }
    Canvas(modifier.size(layout.width.dp, layout.height.dp)) {
        val radius = DOT_MATRIX_RADIUS_FRACTION * dotSize.toPx()
        layout.centres.forEach { drawCircle(color, radius, Offset(it.x.dp.toPx(), it.y.dp.toPx())) }
    }
}

@Composable
private fun ThinNumerals(
    text: String,
    dotSize: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fontSize = with(LocalDensity.current) { (dotSize * THIN_FONT_SCALE).toSp() }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style =
            TextStyle(
                fontFamily = UrbanistFontFamily,
                fontWeight = FontWeight.ExtraLight,
                fontSize = fontSize,
                lineHeight = fontSize,
                letterSpacing = (-0.03).em,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        maxLines = 1,
        softWrap = false,
    )
}

private val previewSizes = listOf(4, 5, 6, 8, 9)
private val previewTexts = listOf("2:33", "23", "52", "14:52", "482 913")

@Composable
private fun DotMatrixPreviewBody(numerals: Numerals) {
    StayFocusedTheme {
        Column(
            modifier = Modifier.background(StayFocusedTheme.colors.ink).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            previewSizes.forEach { s ->
                DotMatrixText("2:33", "2 hours 33 minutes", s.dp, Color.White, numerals = numerals)
            }
            previewTexts.drop(1).forEach { t ->
                DotMatrixText(t, t, 5.dp, Color.White, numerals = numerals)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                DotMatrixText("30", "30 minutes", 4.dp, Color.White, numerals = numerals)
                Text("min", color = Color.White)
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 760)
@Composable
internal fun DotMatrixTextPreview() = DotMatrixPreviewBody(Numerals.Dots)

@Preview(widthDp = 390, heightDp = 760)
@Composable
internal fun DotMatrixTextThinPreview() = DotMatrixPreviewBody(Numerals.Thin)
