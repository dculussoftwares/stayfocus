package com.dculus.stayfocused.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.navigation.Account
import com.dculus.stayfocused.core.navigation.Block
import com.dculus.stayfocused.core.navigation.BlockWizard
import com.dculus.stayfocused.core.navigation.Devices
import com.dculus.stayfocused.core.navigation.Home
import com.dculus.stayfocused.core.navigation.Insights
import com.dculus.stayfocused.core.navigation.LinkAdd
import com.dculus.stayfocused.core.navigation.LinkConfirm
import com.dculus.stayfocused.core.navigation.LinkScan
import com.dculus.stayfocused.core.navigation.Permissions
import com.dculus.stayfocused.core.navigation.RemoteDevice
import com.dculus.stayfocused.core.navigation.SignIn
import com.dculus.stayfocused.core.navigation.Welcome
import com.dculus.stayfocused.core.sync.FakeAuthRepository
import com.dculus.stayfocused.core.testing.FakeBlockRepository
import com.dculus.stayfocused.core.testing.FakeBreakRepository
import com.dculus.stayfocused.core.testing.FakeLinkedDevicesRepository
import com.dculus.stayfocused.core.testing.FakeLockedAppsRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import com.dculus.stayfocused.core.ui.components.NavPlaceholderScreen
import com.dculus.stayfocused.core.ui.components.SF_PLACEHOLDER_LIST_TAG
import com.dculus.stayfocused.core.ui.components.SF_TAB_BAR_TAG
import com.dculus.stayfocused.core.ui.components.sfTabTag
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.usage.DayUsageStats
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import com.dculus.stayfocused.core.usage.UsageAverages
import com.dculus.stayfocused.core.usage.UsageRepository
import com.dculus.stayfocused.feature.block.BlockViewModel
import com.dculus.stayfocused.feature.block.TargetApp
import com.dculus.stayfocused.feature.block.TargetAppsProvider
import com.dculus.stayfocused.feature.block.WizardViewModel
import com.dculus.stayfocused.feature.home.HomeViewModel
import com.dculus.stayfocused.feature.onboarding.SignInViewModel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class AppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    private val blockViewModel =
        BlockViewModel(
            FakeBlockRepository(),
            FakeLinkedDevicesRepository(),
            object : InstalledAppsRepository {
                override fun observeLaunchableApps() = flowOf(emptyList<AppInfo>())
            },
            FakeSettingsRepository(),
            FakeLockedAppsRepository(),
            object : UsageRepository {
                override fun today() = emptyFlow<DayUsageStats>()

                override fun requestRefresh() = Unit

                override fun day(date: LocalDate) = emptyFlow<DayUsageStats?>()

                override fun averages() = emptyFlow<UsageAverages>()

                override fun blockedToday() = emptyFlow<Int>()

                override suspend fun backfill() = Unit
            },
        )

    private val signInViewModel = SignInViewModel(FakeAuthRepository(), FakeSettingsRepository())
    private val homeViewModel = HomeViewModel(FakeBreakRepository(), Clock.systemUTC())
    private val wizardViewModel =
        WizardViewModel(
            SavedStateHandle(),
            object : TargetAppsProvider {
                override fun apps(target: BlockTarget) = flowOf(emptyList<TargetApp>())
            },
            FakeLinkedDevicesRepository(),
            FakeSettingsRepository(),
            FakeBlockRepository(),
            Clock.systemUTC(),
        )

    private fun launch(startGraph: StartGraph = StartGraph.MAIN) {
        composeRule.setContent {
            navController = rememberNavController()
            StayFocusedTheme {
                StayFocusedNavigation(
                    startGraph = startGraph,
                    navController = navController,
                    blockViewModel = { blockViewModel },
                    wizardViewModel = { wizardViewModel },
                    insightsContent = { NavPlaceholderScreen(title = "Insights") },
                    homeViewModel = { homeViewModel },
                    signInViewModel = { signInViewModel },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun navigate(route: Any) {
        composeRule.runOnUiThread { navController.navigate(route) }
        composeRule.waitForIdle()
    }

    private fun tab(tab: MainTab) = composeRule.onNodeWithTag(sfTabTag(tab.name))

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun currentIs(route: kotlin.reflect.KClass<*>) = navController.currentDestination?.hasRoute(route) == true

    private fun tabBarVisible() = composeRule.onAllNodesWithTag(SF_TAB_BAR_TAG).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun tabBarIsVisibleExactlyOnTabsRemoteDeviceAndAccount() {
        launch()
        val shown = listOf(Home, Block, Devices, Insights, RemoteDevice("device"), Account)
        val hidden = listOf(BlockWizard(), LinkAdd, LinkScan, LinkConfirm("token"), Welcome, SignIn, Permissions)
        shown.forEach {
            navigate(it)
            assertTrue(tabBarVisible(), "tab bar should show on $it")
        }
        hidden.forEach {
            navigate(it)
            assertTrue(!tabBarVisible(), "tab bar should be hidden on $it")
        }
    }

    @Test
    fun switchingTabsKeepsScrollState() {
        launch()
        tab(MainTab.INSIGHTS).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SF_PLACEHOLDER_LIST_TAG).performScrollToIndex(20)
        tab(MainTab.HOME).performClick()
        composeRule.waitForIdle()
        tab(MainTab.INSIGHTS).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SF_PLACEHOLDER_LIST_TAG).assert(
            SemanticsMatcher("list is scrolled") {
                (it.config[SemanticsProperties.VerticalScrollAxisRange].value()) > 0f
            },
        )
    }

    @Test
    fun backFromNonHomeTabGoesHome() {
        launch()
        tab(MainTab.BLOCK).performClick()
        composeRule.waitForIdle()
        tab(MainTab.INSIGHTS).performClick()
        composeRule.waitForIdle()
        pressBack()
        assertTrue(currentIs(Home::class), "back from a non-Home tab should land on Home")
        assertTrue(!composeRule.activity.isFinishing)
    }

    @Test
    fun backFromHomeExits() {
        launch()
        pressBack()
        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun reselectingATabPopsToItsRoot() {
        launch()
        tab(MainTab.DEVICES).performClick()
        composeRule.waitForIdle()
        navigate(RemoteDevice("device"))
        tab(MainTab.DEVICES).assertIsSelected()
        tab(MainTab.DEVICES).performClick()
        composeRule.waitForIdle()
        assertTrue(currentIs(Devices::class))
    }

    @Test
    fun tabStackIsSavedAndRestored() {
        launch()
        tab(MainTab.DEVICES).performClick()
        composeRule.waitForIdle()
        navigate(RemoteDevice("device"))
        tab(MainTab.BLOCK).performClick()
        composeRule.waitForIdle()
        tab(MainTab.DEVICES).performClick()
        composeRule.waitForIdle()
        assertTrue(currentIs(RemoteDevice::class), "Devices should restore its sub-screen")
    }

    @Test
    fun accountOpensFromHomeWithNoTabSelected() {
        launch()
        composeRule.onNodeWithText("Open Account").performClick()
        composeRule.waitForIdle()
        assertTrue(currentIs(Account::class))
        MainTab.entries.forEach { tab(it).assertIsNotSelected() }
        tab(MainTab.BLOCK).performClick()
        composeRule.waitForIdle()
        assertTrue(currentIs(Block::class))
    }

    @Test
    fun onboardingRunsToHomeAndShowsNoTabBarUntilThen() {
        launch(StartGraph.ONBOARDING)
        assertTrue(currentIs(Welcome::class))
        assertTrue(!tabBarVisible())
        composeRule.onNodeWithText("Get started").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Continue without an account").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { currentIs(Permissions::class) }
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitForIdle()
        assertTrue(currentIs(Home::class))
        assertTrue(tabBarVisible())
        assertEquals(null, navController.currentBackStack.value.firstOrNull { it.destination.hasRoute(Welcome::class) })
    }
}
