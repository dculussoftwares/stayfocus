package com.dculus.stayfocused.core.ui.v5.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import com.dculus.stayfocused.core.ui.components.SF_TOAST_DURATION_MS
import com.dculus.stayfocused.core.ui.components.ToastController
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h780dp-xxhdpi")
class V5SheetBehaviourTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var visible by mutableStateOf(true)
    private var dismissals = 0

    private fun setSheet() {
        composeRule.setContent {
            V5Theme {
                V5BottomSheet(
                    visible = visible,
                    onDismiss = {
                        dismissals++
                        visible = false
                    },
                ) { androidx.compose.material3.Text("SHEET BODY") }
            }
        }
    }

    @Test
    fun scrimTapClosesSheet() {
        setSheet()
        composeRule.onNodeWithText("SHEET BODY").assertExists()
        composeRule.onNodeWithTag(V5_SHEET_SCRIM_TAG).performClick()
        assertEquals(1, dismissals)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("SHEET BODY").assertDoesNotExist()
    }

    @Test
    fun backClosesSheet() {
        setSheet()
        Espresso.pressBack()
        assertEquals(1, dismissals)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("SHEET BODY").assertDoesNotExist()
    }

    @Test
    fun panelTapDoesNotClose() {
        setSheet()
        composeRule.onNodeWithText("SHEET BODY").performClick()
        assertEquals(0, dismissals)
    }

    @Test
    fun dragDownClosesSheet() {
        setSheet()
        composeRule.onNodeWithTag(V5_SHEET_PANEL_TAG).performTouchInput { swipeDown(durationMillis = 150) }
        composeRule.waitForIdle()
        assertEquals(1, dismissals)
        composeRule.onNodeWithText("SHEET BODY").assertDoesNotExist()
    }

    @Test
    fun dragUpKeepsSheetOpen() {
        setSheet()
        composeRule.onNodeWithTag(V5_SHEET_PANEL_TAG).performTouchInput { swipeUp(durationMillis = 150) }
        composeRule.waitForIdle()
        assertEquals(0, dismissals)
        composeRule.onNodeWithText("SHEET BODY").assertExists()
    }

    @Test
    fun shortSlowDragSnapsBackToRestingPosition() {
        setSheet()
        val panel = composeRule.onNodeWithTag(V5_SHEET_PANEL_TAG)
        val restingTop = panel.getUnclippedBoundsInRoot().top
        panel.performTouchInput {
            down(center)
            moveBy(Offset(0f, 8.dp.toPx()), delayMillis = 400)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(0, dismissals)
        composeRule.onNodeWithText("SHEET BODY").assertExists()
        assertEquals(restingTop.value, panel.getUnclippedBoundsInRoot().top.value, 0.5f)
    }

    @Test
    fun accessibilityDismissActionCloses() {
        setSheet()
        composeRule.onNodeWithTag(V5_SHEET_PANEL_TAG).performSemanticsAction(SemanticsActions.Dismiss)
        assertEquals(1, dismissals)
    }

    @Test
    fun repeatedDismissDuringExitNotifiesOnce() {
        setSheet()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(V5_SHEET_SCRIM_TAG).performClick()
        assertEquals(1, dismissals)
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(32)
        Espresso.pressBack()
        assertEquals(1, dismissals)
    }

    @Test
    fun twoDismissEventsInOneFrameNotifyOnce() {
        setSheet()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(V5_SHEET_SCRIM_TAG).performClick()
        Espresso.pressBack()
        assertEquals(1, dismissals)
    }

    @Test
    fun dismissedBeforeEntranceFinishesStillPlaysExit() {
        composeRule.mainClock.autoAdvance = false
        setSheet()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithText("SHEET BODY").assertExists()
        composeRule.onNodeWithTag(V5_SHEET_SCRIM_TAG).performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(50)
        composeRule.onNodeWithText("SHEET BODY").assertExists()
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithText("SHEET BODY").assertDoesNotExist()
    }

    @Test
    fun sheetCanBeDismissedAgainWhenCallerKeepsItOpen() {
        composeRule.setContent {
            V5Theme {
                V5BottomSheet(
                    visible = true,
                    onDismiss = { dismissals++ },
                ) { androidx.compose.material3.Text("SHEET BODY") }
            }
        }
        composeRule.onNodeWithTag(V5_SHEET_SCRIM_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(V5_SHEET_SCRIM_TAG).performClick()
        assertEquals(2, dismissals)
    }

    @Test
    fun dragPastThresholdSnapsBackWhenCallerKeepsSheetOpen() {
        composeRule.setContent {
            V5Theme {
                V5BottomSheet(
                    visible = true,
                    onDismiss = { dismissals++ },
                ) { androidx.compose.material3.Text("SHEET BODY") }
            }
        }
        val panel = composeRule.onNodeWithTag(V5_SHEET_PANEL_TAG)
        val restingTop = panel.getUnclippedBoundsInRoot().top
        panel.performTouchInput { swipeDown(durationMillis = 150) }
        composeRule.waitForIdle()
        assertEquals(1, dismissals)
        assertEquals(restingTop.value, panel.getUnclippedBoundsInRoot().top.value, 0.5f)
    }

    @Test
    fun dragDismissRule() {
        assertTrue(shouldDismissSheetDrag(offsetPx = 301f, heightPx = 1000f, velocityPxPerSec = 0f))
        assertFalse(shouldDismissSheetDrag(offsetPx = 299f, heightPx = 1000f, velocityPxPerSec = 0f))
        assertTrue(shouldDismissSheetDrag(offsetPx = 40f, heightPx = 1000f, velocityPxPerSec = 2000f))
        assertFalse(shouldDismissSheetDrag(offsetPx = 0f, heightPx = 1000f, velocityPxPerSec = 2000f))
        assertFalse(shouldDismissSheetDrag(offsetPx = 40f, heightPx = 1000f, velocityPxPerSec = -2000f))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h780dp-xxhdpi")
class V5ToastBehaviourTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var controller: ToastController

    private fun setHost() {
        composeRule.setContent {
            V5Theme {
                val scope = rememberCoroutineScope()
                controller = androidx.compose.runtime.remember { ToastController(scope) }
                V5ToastHost(controller)
            }
        }
    }

    @Test
    fun longMessageIsCappedAt320dp() {
        setHost()
        composeRule.runOnUiThread { controller.show("A very long message ".repeat(12)) }
        composeRule.waitForIdle()
        val width = composeRule.onNodeWithTag(V5_TOAST_TAG).getUnclippedBoundsInRoot().let { it.right - it.left }
        assertTrue("toast is $width wide", width <= 320.dp && width > 200.dp)
    }

    @Test
    fun toastShowsThenAutoHidesAfter2600ms() {
        setHost()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { controller.show("Block is on") }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithText("Block is on").assertExists()

        // Clock is at ~416 ms: just before the 2.6 s boundary the message is still set, just after it is cleared.
        composeRule.mainClock.advanceTimeBy(SF_TOAST_DURATION_MS - 416 - 50)
        composeRule.runOnUiThread { assertEquals("Block is on", controller.message) }
        composeRule.onNodeWithText("Block is on").assertExists()

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnUiThread { assertNull(controller.message) }

        // AnimatedContent keeps the outgoing toast for its 150 ms fade-out.
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithText("Block is on").assertDoesNotExist()
    }
}
