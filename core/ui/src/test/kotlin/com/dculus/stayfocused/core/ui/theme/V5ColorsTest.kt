package com.dculus.stayfocused.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class V5ColorsTest {
    private val c = V5Colors()

    private fun hex(color: Color) = "#%08X".format(color.toArgb())

    @Test
    fun `semantic colours match V5_SPEC section 3`() {
        val expected =
            mapOf(
                "background" to (c.background to "#FFEDEDEB"),
                "surface" to (c.surface to "#FFFFFFFF"),
                "surfaceRaised" to (c.surfaceRaised to "#FFF8F8F6"),
                "ink" to (c.ink to "#FF163D22"),
                "textSecondary" to (c.textSecondary to "#FF56604F"),
                "textTertiary" to (c.textTertiary to "#FF38432F"),
                "onInkMuted" to (c.onInkMuted to "#FFC5D6BA"),
                "hairline" to (c.hairline to "#FFD3D4D6"),
                "track" to (c.track to "#FFECECEA"),
                "orange" to (c.orange to "#FFFE5E0E"),
                "orangeGlow" to (c.orangeGlow to "#FFFF9A3C"),
                "orangeGlowDeep" to (c.orangeGlowDeep to "#FFFF7A2E"),
                "orangeSoft" to (c.orangeSoft to "#FFF7934F"),
                "orangeGold" to (c.orangeGold to "#FFFEBE42"),
                "violet" to (c.violet to "#FF4B3FD1"),
                "violetGlow" to (c.violetGlow to "#FF9B8CFF"),
                "violetTint" to (c.violetTint to "#FFE4DFFF"),
                "violetBorder" to (c.violetBorder to "#FFE3DFFA"),
                "pink" to (c.pink to "#FFE8336F"),
                "pinkGlow" to (c.pinkGlow to "#FFEC5A8A"),
                "pinkGlowLight" to (c.pinkGlowLight to "#FFF07FA2"),
                "pinkMid" to (c.pinkMid to "#FFEA4D80"),
                "pinkSoft" to (c.pinkSoft to "#FFE26490"),
                "pinkTint" to (c.pinkTint to "#FFEC6A93"),
                "pinkDeep" to (c.pinkDeep to "#FFD42563"),
                "pinkRose" to (c.pinkRose to "#FFE77A9E"),
                "pinkScan" to (c.pinkScan to "#FFF27BA1"),
                "pinkText" to (c.pinkText to "#FFC21F59"),
                "green" to (c.green to "#FF2F9A3C"),
                "greenGlow" to (c.greenGlow to "#FF9CCB4A"),
                "greenFresh" to (c.greenFresh to "#FF5DB548"),
                "greenLight" to (c.greenLight to "#FFB9DE6A"),
                "greenScanLine" to (c.greenScanLine to "#FFC8E88F"),
                "greenLabel" to (c.greenLabel to "#FF23772F"),
                "success" to (c.success to "#FF2F9E45"),
                "online" to (c.online to "#FF34C759"),
                "onlineBackground" to (c.onlineBackground to "#FFE8F5EA"),
                "onlineText" to (c.onlineText to "#FF1F7A33"),
                "red" to (c.red to "#FFE5242B"),
                "error" to (c.error to "#FFD12A2A"),
                "destructive" to (c.destructive to "#FFC21F26"),
                "lime" to (c.lime to "#FFD8F23C"),
                "scanBackground" to (c.scanBackground to "#FF0D0F12"),
                "inkGlow" to (c.inkGlow to "#FF2C3A50"),
                "google" to (c.google to "#FF4285F4"),
                "onGradient" to (c.onGradient to "#FFFFFFFF"),
            )
        expected.forEach { (name, pair) -> assertEquals(name, pair.second, hex(pair.first)) }
    }

    @Test
    fun `app brand colours match the prototype APPS table`() {
        val b = V5AppBrandColors()
        assertEquals("#FFE1306C", hex(b.instagram))
        assertEquals("#FF111318", hex(b.tiktok))
        assertEquals("#FFFF0033", hex(b.youtube))
        assertEquals("#FFFFD400", hex(b.snapchat))
        assertEquals("#FFFF4500", hex(b.reddit))
        assertEquals("#FF000B1A", hex(b.x))
        assertEquals("#FF22A55A", hex(b.whatsapp))
        assertEquals("#FFB20710", hex(b.netflix))
        assertEquals("#FF6B4FE0", hex(b.games))
    }
}
