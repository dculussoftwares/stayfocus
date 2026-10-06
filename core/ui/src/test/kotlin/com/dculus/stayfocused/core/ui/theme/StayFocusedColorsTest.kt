package com.dculus.stayfocused.core.ui.theme

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class StayFocusedColorsTest {
    private val c = StayFocusedColors()

    private fun hex(argb: Int) = "#%08X".format(argb)

    @Test
    fun `colour roles match the handoff table`() {
        val expected = mapOf(
            "background" to (c.background to "#FF0E0F0D"),
            "panel" to (c.panel to "#FF171915"),
            "panelGradientStart" to (c.panelGradientStart to "#FF1A1C17"),
            "panelGradientEnd" to (c.panelGradientEnd to "#FF141611"),
            "track" to (c.track to "#FF262922"),
            "hairline06" to (c.hairline06 to "#0FFFFFFF"),
            "hairline08" to (c.hairline08 to "#14FFFFFF"),
            "hairline10" to (c.hairline10 to "#1AFFFFFF"),
            "hairline14" to (c.hairline14 to "#24FFFFFF"),
            "text" to (c.text to "#FFF2F4EC"),
            "secondary" to (c.secondary to "#FFA6AB9D"),
            "tertiary" to (c.tertiary to "#FF6E7367"),
            "accent" to (c.accent to "#FFC6F432"),
            "accentHover" to (c.accentHover to "#FFD6FF55"),
            "alert" to (c.alert to "#FFFF5A47"),
            "alertText" to (c.alertText to "#FFFF7A66"),
            "warning" to (c.warning to "#FFFFB547"),
        )
        expected.forEach { (name, pair) -> assertEquals(name, pair.second, hex(pair.first.toArgb())) }
    }

    @Test
    fun `hairline alphas are 6, 8, 10 and 14 percent`() {
        listOf(c.hairline06 to 0.06f, c.hairline08 to 0.08f, c.hairline10 to 0.10f, c.hairline14 to 0.14f)
            .forEach { (color, alpha) -> assertEquals(alpha, color.alpha, 0.005f) }
    }

    @Test
    fun `app tints match the handoff in order`() {
        val expected = listOf("#FFFF9A8B", "#FFFF7A66", "#FFFFB547", "#FF7EE0A1", "#FF9DC3FF", "#FFD9DCD2", "#FFB9A7FF")
        assertEquals(expected, c.appTints.map { hex(it.toArgb()) })
    }

    @Test
    fun `shapes and spacing match the handoff`() {
        val s = StayFocusedSpacing()
        assertEquals(
            listOf(18.dp, 6.dp, 8.dp, 10.dp, 12.dp, 14.dp, 16.dp, 22.dp),
            listOf(s.screen, s.gap6, s.gap8, s.gap10, s.gap12, s.gap14, s.gap16, s.gap22),
        )
    }

    @Test
    fun `material colour scheme maps accent and background`() {
        val scheme = c.toMaterialColorScheme()
        assertEquals(c.accent, scheme.primary)
        assertEquals(c.background, scheme.background)
        assertEquals(c.alert, scheme.error)
    }
}
