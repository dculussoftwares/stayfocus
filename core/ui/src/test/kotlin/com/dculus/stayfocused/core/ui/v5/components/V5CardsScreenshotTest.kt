package com.dculus.stayfocused.core.ui.v5.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Roborazzi baselines for the v5 cards, on white and on gradients, incl. the API 26 (no blur) variant. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class V5CardsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(
        name: String,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { content() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/v5/components/$name.png")
    }

    @Test
    @Config(qualifiers = "w390dp-h640dp-xhdpi")
    fun cardsOnWhite() = capture("V5Cards") { V5CardPreview() }

    @Test
    @Config(qualifiers = "w390dp-h640dp-xhdpi")
    fun cardsOnGradient() = capture("GlowCardsAndGlassTiles") { GlowCardPreview() }

    @Test
    @Config(qualifiers = "w390dp-h200dp-xhdpi")
    fun glassTileBlurAndFallback() = capture("GlassTileBlurAndFallback") { GlassTilePreview() }

    @Test
    @Config(sdk = [26], qualifiers = "w390dp-h200dp-xhdpi")
    fun glassTileOnApi26() = capture("GlassTileApi26") { GlassTilePreview() }

    @Test
    @Config(qualifiers = "w390dp-h480dp-xhdpi")
    fun actionTilesOnWhite() = capture("ActionTiles") { ActionTilePreview() }

    @Test
    @Config(qualifiers = "w390dp-h480dp-xhdpi")
    fun actionTilesOnGradient() = capture("ActionTilesOnGradient") { ActionTileOnGradientPreview() }
}
