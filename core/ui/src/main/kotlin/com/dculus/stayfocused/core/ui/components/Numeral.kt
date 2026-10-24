package com.dculus.stayfocused.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.dculus.stayfocused.core.ui.theme.PlusJakartaSansFontFamily

/**
 * Key numbers (screen time, counts, countdowns, the link code): plain Plus Jakarta Sans ExtraLight (weight 200) with
 * letter spacing -.03 em. Digits share one advance, so a running countdown doesn't jitter.
 *
 * [contentDescription] is what TalkBack announces (e.g. "2 hours 33 minutes").
 */
@Composable
fun Numeral(
    text: String,
    contentDescription: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
        color = color,
        style =
            TextStyle(
                fontFamily = PlusJakartaSansFontFamily,
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
