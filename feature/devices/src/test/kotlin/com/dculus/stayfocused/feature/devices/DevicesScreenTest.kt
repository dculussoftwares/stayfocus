package com.dculus.stayfocused.feature.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.model.TamperKind
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class DevicesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: DevicesUiState,
        onLink: () -> Unit = {},
        onOpen: (String) -> Unit = {},
    ) = composeRule.setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            StayFocusedTheme {
                Box(Modifier.width(360.dp).height(780.dp).background(StayFocusedTheme.colors.background)) {
                    DevicesScreen(state, "Pixel 8", onLink, onOpen)
                }
            }
        }
    }

    @Test
    fun onlineCardShowsTodayBatteryAndTheAppInUse() {
        show(DevicesUiState.Loaded(listOf(device())))
        composeRule.onNodeWithText("Aarav's phone").assertIsDisplayed()
        composeRule.onNodeWithText("ONLINE").assertIsDisplayed()
        composeRule.onNodeWithText("2h 27m").assertIsDisplayed()
        composeRule.onNodeWithText("64%").assertIsDisplayed()
        composeRule.onNodeWithText("YouTube").assertIsDisplayed()
        composeRule.onNodeWithText("THIS PHONE · PIXEL 8 · PARENT · 1 LINKED").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/DevicesOnline.png")
    }

    @Test
    fun offlineCardHidesWhatItWasUsing() {
        show(DevicesUiState.Loaded(listOf(device(online = false))))
        composeRule.onNodeWithText("OFFLINE").assertIsDisplayed()
        composeRule.onNodeWithText("YouTube").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/DevicesOffline.png")
    }

    @Test
    fun alertStateShowsTheBadgeAndTheAlertLine() {
        show(
            DevicesUiState.Loaded(
                listOf(
                    device(
                        alerts = listOf(alert(), alert(TamperKind.OFFLINE, null)),
                        requests = listOf(request()),
                    ),
                ),
            ),
        )
        composeRule.onNodeWithTag(DEVICES_BADGE_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("3", useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText("Accessibility turned off · Offline for a while · 1 request for more time")
            .assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/DevicesAlert.png")
    }

    @Test
    fun emptyStateOffersToLinkAPhone() {
        var links = 0
        show(DevicesUiState.Loaded(emptyList()), onLink = { links++ })
        composeRule.onNodeWithText("No linked phones").assertIsDisplayed()
        composeRule.onNodeWithTag(DEVICES_EMPTY_LINK_TAG).performClick()
        assertEquals(1, links)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/DevicesEmpty.png")
    }

    @Test
    fun loadingShowsNeitherCardsNorTheEmptyState() {
        show(DevicesUiState.Loading)
        composeRule.onNodeWithTag(DEVICES_EMPTY_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(DEVICES_THIS_PHONE_TAG).assertIsDisplayed()
    }

    @Test
    fun tappingACardOpensThatPhone() {
        var opened: String? = null
        show(DevicesUiState.Loaded(listOf(device(id = "kid-1"))), onOpen = { opened = it })
        composeRule.onNodeWithTag(deviceCardTag("kid-1")).performClick()
        assertEquals("kid-1", opened)
    }

    @Test
    fun linkButtonStartsLinking() {
        var links = 0
        show(DevicesUiState.Loaded(listOf(device())), onLink = { links++ })
        composeRule.onNodeWithTag(DEVICES_LINK_TAG).performClick()
        assertEquals(1, links)
    }
}
