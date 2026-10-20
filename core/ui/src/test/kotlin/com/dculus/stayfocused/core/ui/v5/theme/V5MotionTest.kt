package com.dculus.stayfocused.core.ui.v5.theme

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V5MotionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var motion: State<Float>? = null

    private fun setScale(scale: Float) {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun setMotion(inspection: Boolean = false) {
        // With auto-advance on, the test rule cancels infinite animations; turn it off so a running one would show.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides inspection) {
                motion =
                    rememberInfiniteMotion(
                        from = 0f,
                        to = 100f,
                        durationMillis = 1_000,
                        easing = LinearEasing,
                        staticValue = 7f,
                    )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun valueAfter(millis: Long): Float {
        composeRule.mainClock.advanceTimeBy(millis)
        return motion!!.value
    }

    @Test
    fun ruleStopsInInspectionModeAndWhenAnimationsAreOff() {
        assertTrue(infiniteMotionEnabled(inspectionMode = false, animatorDurationScale = 1f))
        assertFalse(infiniteMotionEnabled(inspectionMode = true, animatorDurationScale = 1f))
        assertFalse(infiniteMotionEnabled(inspectionMode = false, animatorDurationScale = 0f))
    }

    @Test
    fun runsWhenAnimationsAreOn() {
        setScale(1f)
        setMotion()
        val early = valueAfter(250)
        val later = valueAfter(250)
        assertNotEquals(early, later, 0.0001f)
    }

    @Test
    fun isStaticWhenAnimationsAreRemoved() {
        setScale(0f)
        setMotion()
        assertEquals(7f, valueAfter(250), 0f)
        assertEquals(7f, valueAfter(500), 0f)
    }

    @Test
    fun isStaticInInspectionMode() {
        setScale(1f)
        setMotion(inspection = true)
        assertEquals(7f, valueAfter(250), 0f)
        assertEquals(7f, valueAfter(500), 0f)
    }
}
