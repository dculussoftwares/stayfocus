package com.dculus.stayfocused.feature.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.v5.components.LocalTabBarPadding
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
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
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalNow provides { Instant.EPOCH.plusSeconds(3 * 3600L) },
        ) {
            CompositionLocalProvider(LocalTabBarPadding provides 82.dp) {
                Box(Modifier.width(360.dp).height(780.dp)) {
                    DevicesScreen(state, "Pixel 8", onLink, onOpen)
                }
            }
        }
    }

    private fun v5Device(
        online: Boolean = true,
        osVersion: String? = "15",
        hourly: List<Int>? = List(24) { if (it in 8..21) (it * 3) % 25 else 0 },
    ) = device(online = online).copy(model = "Pixel 7a", osVersion = osVersion, hourlyMins = hourly)

    @Test
    fun onlineCardShowsModelVersionStatsAndTicks() {
        show(DevicesUiState.Loaded(listOf(v5Device())))
        composeRule.onNodeWithText("Family").assertIsDisplayed()
        composeRule.onNodeWithText("Phones you manage").assertIsDisplayed()
        composeRule.onNodeWithText("Aarav's phone").assertIsDisplayed()
        composeRule.onNodeWithText("Pixel 7a · Android 15").assertIsDisplayed()
        composeRule.onNodeWithText("Online").assertIsDisplayed()
        composeRule.onNodeWithText("2h 27m").assertIsDisplayed()
        composeRule.onNodeWithText("64%").assertIsDisplayed()
        composeRule.onNodeWithText("YouTube").assertIsDisplayed()
        composeRule.onNodeWithText("Using now").assertIsDisplayed()
        composeRule.onNodeWithText("THIS PHONE · PIXEL 8 · PARENT · 1 LINKED").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Install Stay Focused Kids on their phone. It shows a code, and you scan it from here.",
            ).assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/DevicesOnline.png")
    }

    @Test
    fun androidVersionAndTicksAreLeftOutWhenNotReported() {
        show(DevicesUiState.Loaded(listOf(v5Device(osVersion = null, hourly = null))))
        composeRule.onNodeWithText("Pixel 7a").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Screen time by hour today").assertDoesNotExist()
    }

    @Test
    fun offlineCardHidesWhatItWasUsing() {
        show(DevicesUiState.Loaded(listOf(v5Device(online = false))))
        composeRule.onNodeWithText("Offline").assertIsDisplayed()
        composeRule.onNodeWithText("YouTube").assertDoesNotExist()
        composeRule.onNodeWithText("Last seen 3 h ago").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/DevicesOffline.png")
    }

    @Test
    fun attentionLineJoinsRequestsAndProtection() {
        show(DevicesUiState.Loaded(listOf(v5Device().copy(alerts = listOf(alert()), requests = listOf(request())))))
        composeRule.onNodeWithText("1 request waiting · Protection is off").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/DevicesAlert.png")
    }

    @Test
    fun requestsArePluralised() {
        show(DevicesUiState.Loaded(listOf(v5Device().copy(requests = listOf(request(), request().copy(id = "r2"))))))
        composeRule.onNodeWithText("2 requests waiting").assertIsDisplayed()
    }

    @Test
    fun aPhoneOfflineForADayAddsTheOfflineLine() {
        show(DevicesUiState.Loaded(listOf(device(online = false).copy(longOffline = true))))
        composeRule.onNodeWithText("Offline for a while").assertIsDisplayed()
    }

    @Test
    fun emptyStateStillOffersTheLinkTile() {
        var links = 0
        show(DevicesUiState.Loaded(emptyList()), onLink = { links++ })
        composeRule.onNodeWithText("No linked phones").assertIsDisplayed()
        composeRule.onNodeWithTag(DEVICES_LINK_TAG).performClick()
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
}
