package com.dculus.stayfocused.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.dculus.stayfocused.core.navigation.Home
import com.dculus.stayfocused.core.navigation.MainGraph
import com.dculus.stayfocused.core.navigation.OnboardingGraph
import com.dculus.stayfocused.core.ui.components.SfTabBar
import com.dculus.stayfocused.core.ui.components.SfTabItem
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.sfScreenEnter
import com.dculus.stayfocused.core.ui.theme.sfScreenExit
import com.dculus.stayfocused.feature.account.accountScreen
import com.dculus.stayfocused.feature.account.navigateToAccount
import com.dculus.stayfocused.feature.block.blockScreen
import com.dculus.stayfocused.feature.block.blockWizardScreen
import com.dculus.stayfocused.feature.block.navigateToBlockWizard
import com.dculus.stayfocused.feature.devices.devicesScreen
import com.dculus.stayfocused.feature.devices.linkAddScreen
import com.dculus.stayfocused.feature.devices.linkConfirmScreen
import com.dculus.stayfocused.feature.devices.linkScanScreen
import com.dculus.stayfocused.feature.devices.navigateToLinkAdd
import com.dculus.stayfocused.feature.devices.navigateToLinkConfirm
import com.dculus.stayfocused.feature.devices.navigateToLinkScan
import com.dculus.stayfocused.feature.devices.navigateToRemoteDevice
import com.dculus.stayfocused.feature.devices.remoteDeviceScreen
import com.dculus.stayfocused.feature.home.homeScreen
import com.dculus.stayfocused.feature.insights.insightsScreen
import com.dculus.stayfocused.feature.onboarding.onboardingGraph

/** The whole app UI: the nav host plus the floating tab bar on the destinations that show it. */
@Composable
fun StayFocusedNavigation(
    startGraph: StartGraph,
    modifier: Modifier = Modifier,
    onFinishOnboarding: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val destination = navController.currentBackStackEntryAsState().value?.destination
    val tabItems = MainTab.entries.map { tab -> SfTabItem(tab.name, stringResource(tab.label), tab.icon) }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(StayFocusedTheme.colors.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Box(Modifier.weight(1f)) { AppNavHost(navController, startGraph, onFinishOnboarding) }
        if (destination.showsTabBar()) {
            SfTabBar(
                items = tabItems,
                selectedId = destination.selectedTab()?.name,
                onSelect = { id -> navController.navigateToTab(MainTab.valueOf(id)) },
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    startGraph: StartGraph,
    onFinishOnboarding: () -> Unit,
) {
    val density = LocalDensity.current
    val enter = remember(density) { sfScreenEnter(density) }
    val exit = remember { sfScreenExit() }
    NavHost(
        navController = navController,
        startDestination = startGraph.route,
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { enter },
        popExitTransition = { exit },
    ) {
        onboardingGraph(
            navController = navController,
            onFinished = {
                onFinishOnboarding()
                navController.navigate(MainGraph) { popUpTo(OnboardingGraph) { inclusive = true } }
            },
        )
        navigation<MainGraph>(startDestination = Home) {
            homeScreen(onOpenAccount = { navController.navigateToAccount() })
            blockScreen(onOpenWizard = { target, prefill -> navController.navigateToBlockWizard(target, prefill) })
            blockWizardScreen()
            devicesScreen(
                onLinkPhone = { navController.navigateToLinkAdd() },
                onOpenDevice = { navController.navigateToRemoteDevice(it) },
            )
            insightsScreen()
            accountScreen()
            linkAddScreen(onScan = { navController.navigateToLinkScan() })
            linkScanScreen(onScan = { navController.navigateToLinkConfirm(it) })
            linkConfirmScreen()
            remoteDeviceScreen()
        }
    }
}
