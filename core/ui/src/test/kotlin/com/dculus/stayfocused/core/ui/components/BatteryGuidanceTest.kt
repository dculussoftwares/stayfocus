package com.dculus.stayfocused.core.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class BatteryGuidanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun manufacturerMapping() {
        val expected =
            mapOf(
                "Xiaomi" to OemFamily.XIAOMI,
                "Redmi" to OemFamily.XIAOMI,
                "POCO" to OemFamily.XIAOMI,
                "OPPO" to OemFamily.OPPO,
                "realme" to OemFamily.OPPO,
                "vivo" to OemFamily.VIVO,
                "samsung" to OemFamily.SAMSUNG,
                " OnePlus " to OemFamily.ONEPLUS,
                "HUAWEI" to OemFamily.HUAWEI,
                "HONOR" to OemFamily.HUAWEI,
                "Google" to OemFamily.DEFAULT,
                "" to OemFamily.DEFAULT,
            )
        expected.forEach { (maker, family) -> assertEquals(maker, family, OemFamily.fromManufacturer(maker)) }
        assertEquals(OemFamily.DEFAULT, OemFamily.fromManufacturer(null))
    }

    @Test
    fun guideUrls() {
        assertEquals("https://dontkillmyapp.com/xiaomi", OemFamily.XIAOMI.guideUrl)
        assertEquals("https://dontkillmyapp.com", OemFamily.DEFAULT.guideUrl)
    }

    @Test
    fun buttonsInvokeCallbacks() {
        var settings = 0
        var guide: String? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                StayFocusedTheme {
                    BatteryGuidance(
                        onOpenBatterySettings = { settings++ },
                        onOpenGuide = { guide = it },
                        family = OemFamily.SAMSUNG,
                    )
                }
            }
        }
        composeRule.onNodeWithTag(BATTERY_GUIDANCE_SETTINGS_TAG).performClick()
        composeRule.onNodeWithTag(BATTERY_GUIDANCE_GUIDE_TAG).performClick()
        assertEquals(1, settings)
        assertEquals("https://dontkillmyapp.com/samsung", guide)
    }

    @Test
    fun screenshot() {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { BatteryGuidancePreview() }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/components/BatteryGuidance.png")
    }
}
