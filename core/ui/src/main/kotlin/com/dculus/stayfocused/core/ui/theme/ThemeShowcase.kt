package com.dculus.stayfocused.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Renders every token: colours, type roles, shapes and spacing. Used by the preview and the screenshot test. */
@Composable
internal fun ThemeShowcase() {
    val c = StayFocusedTheme.colors
    val t = StayFocusedTheme.type
    val s = StayFocusedTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.background)
            .padding(s.screen),
        verticalArrangement = Arrangement.spacedBy(s.gap16),
    ) {
        Text("COLOURS", style = t.label, color = c.tertiary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(s.gap8),
            verticalArrangement = Arrangement.spacedBy(s.gap8),
        ) {
            listOf(
                "background" to c.background, "panel" to c.panel, "gradStart" to c.panelGradientStart,
                "gradEnd" to c.panelGradientEnd, "track" to c.track, "hair06" to c.hairline06,
                "hair08" to c.hairline08, "hair10" to c.hairline10, "hair14" to c.hairline14,
                "text" to c.text, "secondary" to c.secondary, "tertiary" to c.tertiary,
                "accent" to c.accent, "accentHov" to c.accentHover, "alert" to c.alert,
                "alertText" to c.alertText, "warning" to c.warning,
            ).forEach { (name, color) -> Swatch(name, color) }
            c.appTints.forEachIndexed { i, color -> Swatch("tint$i", color) }
        }
        Text("TYPE", style = t.label, color = c.tertiary)
        TypeRow("displayXL 54", t.displayXL)
        TypeRow("displayL 44", t.displayL)
        TypeRow("displayM 38", t.displayM)
        TypeRow("display 34", t.display)
        TypeRow("displayS 26", t.displayS)
        TypeRow("title 22", t.title)
        TypeRow("bodyL 16", t.bodyL)
        TypeRow("body 14", t.body)
        TypeRow("bodyS 12.5", t.bodyS)
        Text("LABEL 11 MONO", style = t.label, color = c.secondary)
        Text("LABELS 10 MONO", style = t.labelS, color = c.secondary)
        Text("0123456789", style = t.numeric.copy(fontSize = 60.sp), color = c.accent)
        Text("SHAPES", style = t.label, color = c.tertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(s.gap8)) {
            listOf(
                "chip" to StayFocusedTheme.shapes.chip, "input" to StayFocusedTheme.shapes.input,
                "card" to StayFocusedTheme.shapes.card, "hero" to StayFocusedTheme.shapes.hero,
                "sheet" to StayFocusedTheme.shapes.sheet,
            ).forEach { (name, shape) ->
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(c.panel, shape)
                        .border(1.dp, c.hairline14, shape),
                ) { Text(name, style = t.labelS, color = c.secondary, modifier = Modifier.padding(4.dp)) }
            }
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    val c = StayFocusedTheme.colors
    Column(modifier = Modifier.width(72.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .size(36.dp)
                .background(color, RoundedCornerShape(8.dp))
                .border(1.dp, c.hairline14, RoundedCornerShape(8.dp)),
        )
        Text(name, style = StayFocusedTheme.type.labelS, color = c.secondary)
    }
}

@Composable
private fun TypeRow(name: String, style: TextStyle) {
    Text(name, style = style, color = StayFocusedTheme.colors.text)
}

@Preview(widthDp = 411, heightDp = 1600)
@Composable
private fun ThemeShowcasePreview() {
    StayFocusedTheme { ThemeShowcase() }
}
