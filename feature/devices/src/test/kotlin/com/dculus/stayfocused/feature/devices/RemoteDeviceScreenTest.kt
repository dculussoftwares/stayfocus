package com.dculus.stayfocused.feature.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.model.TamperKind
import com.dculus.stayfocused.core.sync.KidsRule
import com.dculus.stayfocused.core.sync.KidsRuleType
import com.dculus.stayfocused.core.sync.RemoteBlock
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
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-xxhdpi")
class RemoteDeviceScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-10-17T10:00:00Z")

    private val apps =
        listOf(
            RemoteAppRow("a.yt", "YouTube", 80, 1f, locked = false),
            RemoteAppRow("a.ig", "Instagram", 40, 0.5f, locked = true),
            RemoteAppRow("a.wa", "WhatsApp", 0, 0f, locked = false),
        )

    private val blocks =
        listOf(
            RemoteBlock(KidsRule("b1", "Instagram", KidsRuleType.Limit, limitMins = 30), enabled = true),
            RemoteBlock(
                KidsRule("b2", "School night", KidsRuleType.Schedule, start = "22:00", end = "07:00"),
                enabled = false,
            ),
        )

    private fun ready(
        online: Boolean = true,
        focusEndsAt: Instant? = null,
        alerts: List<RemoteAlertCard> = emptyList(),
        requests: List<RemoteRequestCard> = emptyList(),
        blocks: List<RemoteBlock> = this.blocks,
        apps: List<RemoteAppRow> = this.apps,
    ) = RemoteUiState.Ready(
        device =
            device(online = online).copy(
                model = "Pixel 7a",
                hourlyMins = List(24) { if (it in 15..21) 20 else 0 },
                focusEndsAt = focusEndsAt,
                lastSeen = now.minusSeconds(3 * 3600L),
            ),
        alerts = alerts,
        requests = requests,
        blocks = blocks,
        apps = apps,
        todayMins = 147,
    )

    private fun show(
        state: RemoteUiState,
        actions: RemoteActions = RemoteActions(),
    ) = composeRule.setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            Box(Modifier.width(360.dp).height(1400.dp)) {
                RemoteDeviceScreen(state, { now }, actions)
            }
        }
    }

    private val accessibilityAlert =
        RemoteAlertCard("a1", TamperKind.PERMISSION_LOST, "accessibility", "10:42", agoMins = 4)
    private val askForMore = RemoteRequestCard("r1", "Instagram", 15, 2)

    @Test
    fun onlineHeaderShowsBatteryAppAndTodaysTotal() {
        show(ready())
        composeRule.onNodeWithText("Aarav's phone").assertIsDisplayed()
        composeRule.onNodeWithText("Pixel 7a · 64% battery").assertIsDisplayed()
        composeRule.onNodeWithText("Online").assertIsDisplayed()
        composeRule.onNodeWithText("Screen time today").assertIsDisplayed()
        composeRule.onNodeWithText("Using YouTube now").assertIsDisplayed()
        composeRule.onNodeWithText("Blocks on this phone").assertIsDisplayed()
        composeRule.onNodeWithText("New block").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/RemoteOnline.png")
    }

    @Test
    fun offlineHeaderHidesTheAppInUse() {
        show(ready(online = false))
        composeRule.onNodeWithText("Pixel 7a · 64% battery · Last seen 3 h ago").assertIsDisplayed()
        composeRule.onNodeWithText("Using YouTube now").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/RemoteOffline.png")
    }

    @Test
    fun tamperAlertCardRemindsAndDismisses() {
        val reminded = mutableListOf<RemoteAlertCard>()
        val dismissed = mutableListOf<RemoteAlertCard>()
        show(
            ready(alerts = listOf(accessibilityAlert)),
            RemoteActions(onRemind = { reminded += it }, onDismiss = { dismissed += it }),
        )
        composeRule.onNodeWithText("Protection off").assertIsDisplayed()
        composeRule.onNodeWithText("4 min ago").assertIsDisplayed()
        composeRule
            .onNodeWithText("Accessibility was turned off on Aarav's phone. Blocks are paused until it's back on.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Send reminder").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/RemoteAlert.png")
        composeRule.onNodeWithTag(REMOTE_REMIND_TAG).performClick()
        composeRule.onNodeWithTag(REMOTE_DISMISS_TAG).performClick()
        assertEquals(listOf(accessibilityAlert), reminded)
        assertEquals(listOf(accessibilityAlert), dismissed)
    }

    @Test
    fun anAlertThatIsNotAPermissionOnlyOffersDismiss() {
        show(ready(alerts = listOf(RemoteAlertCard("a2", TamperKind.UNLINKED, null, "09:00", agoMins = 90))))
        composeRule.onNodeWithText("Phone was unlinked").assertIsDisplayed()
        composeRule.onNodeWithTag(REMOTE_REMIND_TAG).assertDoesNotExist()
    }

    @Test
    fun unlockRequestCardOffersFourChoices() {
        val decisions = mutableListOf<Int>()
        show(ready(requests = listOf(askForMore)), RemoteActions(onDecide = { _, mins -> decisions += mins }))
        composeRule.onNodeWithText("Request").assertIsDisplayed()
        composeRule.onNodeWithText("2 min ago").assertIsDisplayed()
        composeRule.onNodeWithText("Aarav's phone wants 15 more minutes on Instagram.").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/RemoteRequest.png")
        listOf("15m", "30m", "1h", "No").forEach { composeRule.onNodeWithText(it).performClick() }
        assertEquals(listOf(15, 30, 60, 0), decisions)
    }

    @Test
    fun idleFocusTileStartsAnOfficialTwentyFiveMinuteSessionAndTheExtrasStartMore() {
        val started = mutableListOf<Int>()
        show(ready(), RemoteActions(onStartFocus = { started += it }))
        composeRule.onNodeWithText("Focus 25 min").assertIsDisplayed()
        composeRule.onNodeWithText("Start a study session").assertIsDisplayed()
        composeRule.onNodeWithTag(REMOTE_FOCUS_TAG).performClick()
        listOf(50, 90).forEach { composeRule.onNodeWithTag(remoteFocusTag(it)).performClick() }
        assertEquals(listOf(25, 50, 90), started)
    }

    @Test
    fun runningFocusTileShowsTheCountdownAndStops() {
        var stops = 0
        show(ready(focusEndsAt = now.plusSeconds(12 * 60 + 5)), RemoteActions(onStopFocus = { stops++ }))
        composeRule.onNodeWithText("Focus is on").assertIsDisplayed()
        composeRule.onNodeWithText("12:05 left · tap to stop").assertIsDisplayed()
        composeRule.onNodeWithTag(remoteFocusTag(50)).assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/RemoteFocusRunning.png")
        composeRule.onNodeWithTag(REMOTE_FOCUS_STOP_TAG).performClick()
        assertEquals(1, stops)
    }

    @Test
    fun aFocusSessionThatAlreadyEndedShowsTheStartTileAgain() {
        show(ready(focusEndsAt = now.minusSeconds(5)))
        composeRule.onNodeWithText("Focus 25 min").assertIsDisplayed()
    }

    @Test
    fun lockTileLocksEveryApp() {
        var locks = 0
        show(ready(), RemoteActions(onLockAll = { locks++ }))
        composeRule.onNodeWithText("Lock all apps").assertIsDisplayed()
        composeRule.onNodeWithText("Calls still work").assertIsDisplayed()
        composeRule.onNodeWithTag(REMOTE_LOCK_ALL_TAG).performClick()
        assertEquals(1, locks)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/RemoteLocks.png")
    }

    @Test
    fun lockTileOfAnAllLockedPhoneUnlocks() {
        var unlocks = 0
        val locked = ready().let { it.copy(device = it.device.copy(allLocked = true)) }
        show(locked, RemoteActions(onUnlockAll = { unlocks++ }))
        composeRule.onNodeWithText("Unlock apps").assertIsDisplayed()
        composeRule.onNodeWithText("Everything is locked now").assertIsDisplayed()
        composeRule.onNodeWithTag(REMOTE_LOCK_ALL_TAG).performClick()
        assertEquals(1, unlocks)
    }

    @Test
    fun blockRowsToggleAndNewBlockOpensTheWizard() {
        val toggles = mutableListOf<Pair<String, Boolean>>()
        var newBlocks = 0
        show(ready(), RemoteActions(onBlockEnabled = { id, on -> toggles += id to on }, onNewBlock = { newBlocks++ }))
        composeRule.onNodeWithText("30 min a day").assertIsDisplayed()
        composeRule.onNodeWithText("Every day · 22:00–07:00").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("School night block").performClick()
        composeRule.onNodeWithTag(REMOTE_NEW_BLOCK_TAG).performClick()
        assertEquals(listOf("b2" to true), toggles)
        assertEquals(1, newBlocks)
    }

    @Test
    fun appLockTogglesShowLockedAppsAndSendTheChange() {
        val locks = mutableListOf<Pair<String, Boolean>>()
        show(ready(), RemoteActions(onLock = { app, on -> locks += app.pkg to on }))
        composeRule.onNodeWithTag(remoteLockTag("a.yt")).performClick()
        composeRule.onNodeWithTag(remoteLockTag("a.ig")).performClick()
        assertEquals(listOf("a.yt" to true, "a.ig" to false), locks)
    }

    @Test
    fun unlinkButtonUnlinksUnlessOneIsRunning() {
        var unlinks = 0
        show(ready(), RemoteActions(onUnlink = { unlinks++ }))
        composeRule.onNodeWithTag(REMOTE_LIST_TAG).performScrollToNode(hasTestTag(REMOTE_UNLINK_TAG))
        composeRule.onNodeWithTag(REMOTE_UNLINK_TAG).performClick()
        assertEquals(1, unlinks)
    }

    @Test
    fun loadingShowsOnlyTheBackButton() {
        show(RemoteUiState.Loading)
        composeRule.onNodeWithTag(LINK_BACK_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(REMOTE_UNLINK_TAG).assertDoesNotExist()
    }
}
