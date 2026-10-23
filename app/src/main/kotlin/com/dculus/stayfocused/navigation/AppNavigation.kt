package com.dculus.stayfocused.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import androidx.navigation.navigation
import com.dculus.stayfocused.core.navigation.Devices
import com.dculus.stayfocused.core.navigation.Home
import com.dculus.stayfocused.core.navigation.LinkScan
import com.dculus.stayfocused.core.navigation.MainGraph
import com.dculus.stayfocused.core.navigation.OnboardingGraph
import com.dculus.stayfocused.core.ui.components.LocalTabBarPadding
import com.dculus.stayfocused.core.ui.components.PillTab
import com.dculus.stayfocused.core.ui.components.PillTabBar
import com.dculus.stayfocused.core.ui.components.ToastController
import com.dculus.stayfocused.core.ui.components.V5ToastHost
import com.dculus.stayfocused.core.ui.components.recordBackdrop
import com.dculus.stayfocused.core.ui.components.rememberPillTabBarBackdrop
import com.dculus.stayfocused.core.ui.components.rememberPillTabBarContentPadding
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.v5ScreenEnter
import com.dculus.stayfocused.core.ui.theme.v5ScreenExit
import com.dculus.stayfocused.feature.account.AccountViewModel
import com.dculus.stayfocused.feature.account.accountScreen
import com.dculus.stayfocused.feature.account.navigateToAccount
import com.dculus.stayfocused.feature.block.BlockViewModel
import com.dculus.stayfocused.feature.block.DescribeViewModel
import com.dculus.stayfocused.feature.block.WizardViewModel
import com.dculus.stayfocused.feature.block.blockDescribeScreen
import com.dculus.stayfocused.feature.block.blockScreen
import com.dculus.stayfocused.feature.block.blockWizardScreen
import com.dculus.stayfocused.feature.block.closeBlockDescribe
import com.dculus.stayfocused.feature.block.closeBlockWizard
import com.dculus.stayfocused.feature.block.finishBlockWizard
import com.dculus.stayfocused.feature.block.navigateToBlockDescribe
import com.dculus.stayfocused.feature.block.navigateToBlockEditor
import com.dculus.stayfocused.feature.block.navigateToBlockWizard
import com.dculus.stayfocused.feature.block.openWizardFromDescribe
import com.dculus.stayfocused.feature.block.replaceWizardWithDescribe
import com.dculus.stayfocused.feature.devices.DevicesViewModel
import com.dculus.stayfocused.feature.devices.LinkAddViewModel
import com.dculus.stayfocused.feature.devices.LinkConfirmViewModel
import com.dculus.stayfocused.feature.devices.RemoteDeviceViewModel
import com.dculus.stayfocused.feature.devices.devicesScreen
import com.dculus.stayfocused.feature.devices.linkAddScreen
import com.dculus.stayfocused.feature.devices.linkConfirmScreen
import com.dculus.stayfocused.feature.devices.linkScanScreen
import com.dculus.stayfocused.feature.devices.navigateToLinkAdd
import com.dculus.stayfocused.feature.devices.navigateToLinkConfirm
import com.dculus.stayfocused.feature.devices.navigateToLinkScan
import com.dculus.stayfocused.feature.devices.navigateToRemoteDevice
import com.dculus.stayfocused.feature.devices.remoteDeviceScreen
import com.dculus.stayfocused.feature.home.HomeDestinations
import com.dculus.stayfocused.feature.home.HomeViewModel
import com.dculus.stayfocused.feature.home.homeScreen
import com.dculus.stayfocused.feature.insights.insightsScreen
import com.dculus.stayfocused.feature.onboarding.PermissionsViewModel
import com.dculus.stayfocused.feature.onboarding.SignInViewModel
import com.dculus.stayfocused.feature.onboarding.navigateToSignIn
import com.dculus.stayfocused.feature.onboarding.onboardingGraph

/** The whole app UI: the nav host plus the floating tab bar on the destinations that show it. */
@Composable
fun StayFocusedNavigation(
    startGraph: StartGraph,
    modifier: Modifier = Modifier,
    onFinishOnboarding: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    blockViewModel: (@Composable () -> BlockViewModel)? = null,
    wizardViewModel: (@Composable () -> WizardViewModel)? = null,
    describeViewModel: (@Composable () -> DescribeViewModel)? = null,
    insightsContent: (@Composable () -> Unit)? = null,
    homeViewModel: (@Composable () -> HomeViewModel)? = null,
    signInViewModel: (@Composable () -> SignInViewModel)? = null,
    permissionsViewModel: (@Composable () -> PermissionsViewModel)? = null,
    accountViewModel: (@Composable () -> AccountViewModel)? = null,
    linkAddViewModel: (@Composable () -> LinkAddViewModel)? = null,
    linkConfirmViewModel: (@Composable () -> LinkConfirmViewModel)? = null,
    devicesViewModel: (@Composable () -> DevicesViewModel)? = null,
    remoteDeviceViewModel: (@Composable () -> RemoteDeviceViewModel)? = null,
) {
    val scope = rememberCoroutineScope()
    val toast = remember(scope) { ToastController(scope) }
    val destination = navController.currentBackStackEntryAsState().value?.destination
    val tabs = MainTab.entries.map { tab -> PillTab(tab.name, stringResource(tab.label)) }
    val showTabBar = destination.showsTabBar()
    val tabBarPadding = rememberPillTabBarContentPadding()
    val backdrop = rememberPillTabBarBackdrop()
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(StayFocusedTheme.colors.background)
                .statusBarsPadding(),
    ) {
        // Tab screens draw under the floating bar (they pad their lists with LocalTabBarPadding); other screens
        // keep clear of the navigation bar themselves.
        Box(
            (if (showTabBar) Modifier.fillMaxSize() else Modifier.fillMaxSize().navigationBarsPadding())
                .recordBackdrop(backdrop),
        ) {
            CompositionLocalProvider(LocalTabBarPadding provides if (showTabBar) tabBarPadding else 0.dp) {
                AppNavHost(
                    navController,
                    startGraph,
                    onFinishOnboarding,
                    blockViewModel,
                    wizardViewModel,
                    describeViewModel,
                    insightsContent,
                    homeViewModel,
                    signInViewModel,
                    permissionsViewModel,
                    accountViewModel,
                    linkAddViewModel,
                    linkConfirmViewModel,
                    devicesViewModel,
                    remoteDeviceViewModel,
                    toast::show,
                )
            }
        }
        StayFocusedTheme {
            V5ToastHost(toast, bottomOffset = if (showTabBar) TOAST_ABOVE_TAB_BAR else TOAST_ABOVE_NAV_BAR)
            if (showTabBar) {
                PillTabBar(
                    tabs = tabs,
                    selectedId = destination.selectedTab()?.name,
                    onSelect = { id -> navController.navigateToTab(MainTab.valueOf(id)) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    backdrop = backdrop,
                )
            }
        }
    }
}

private val TOAST_ABOVE_TAB_BAR = 96.dp
private val TOAST_ABOVE_NAV_BAR = 24.dp

@Composable
private fun AppNavHost(
    navController: NavHostController,
    startGraph: StartGraph,
    onFinishOnboarding: () -> Unit,
    blockViewModel: (@Composable () -> BlockViewModel)?,
    wizardViewModel: (@Composable () -> WizardViewModel)?,
    describeViewModel: (@Composable () -> DescribeViewModel)?,
    insightsContent: (@Composable () -> Unit)?,
    homeViewModel: (@Composable () -> HomeViewModel)?,
    signInViewModel: (@Composable () -> SignInViewModel)?,
    permissionsViewModel: (@Composable () -> PermissionsViewModel)?,
    accountViewModel: (@Composable () -> AccountViewModel)?,
    linkAddViewModel: (@Composable () -> LinkAddViewModel)?,
    linkConfirmViewModel: (@Composable () -> LinkConfirmViewModel)?,
    devicesViewModel: (@Composable () -> DevicesViewModel)?,
    remoteDeviceViewModel: (@Composable () -> RemoteDeviceViewModel)?,
    showToast: (String) -> Unit,
) {
    val density = LocalDensity.current
    val enter = remember(density) { v5ScreenEnter(density) }
    val exit = remember { v5ScreenExit() }
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
            signInViewModel = signInViewModel,
            permissionsViewModel = permissionsViewModel,
            onFinished = { toast ->
                onFinishOnboarding()
                showToast(toast)
                // Clears everything, so signing in from Account does not leave the old Account underneath the new Home.
                navController.navigate(MainGraph) { popUpTo(navController.graph.id) { inclusive = true } }
            },
        )
        navigation<MainGraph>(startDestination = Home) {
            homeScreen(
                destinations =
                    HomeDestinations(
                        onOpenAccount = { navController.navigateToAccount() },
                        onNewBlock = { navController.navigateToBlockWizard() },
                        onAiDescribe = { navController.navigateToBlockDescribe() },
                        onOpenInsights = { navController.navigateToTab(MainTab.INSIGHTS) },
                        onOpenBlock = { navController.navigateToTab(MainTab.BLOCK) },
                        onOpenDevices = { navController.navigateToTab(MainTab.DEVICES) },
                        onOpenDevice = {
                            // Devices root underneath, so reselecting the Devices tab pops back to it.
                            navController.navigateToTab(MainTab.DEVICES)
                            // A restored Devices stack may still hold an earlier phone: start from its root.
                            navController.popBackStack(Devices, inclusive = false)
                            navController.navigateToRemoteDevice(it)
                        },
                        onLinkPhone = { navController.navigateToLinkAdd() },
                    ),
                viewModelProvider = homeViewModel,
            )
            blockScreen(
                onOpenWizard = { target, prefill -> navController.navigateToBlockWizard(target, prefill) },
                onDescribe = { target -> navController.navigateToBlockDescribe(target) },
                onEditBlock = { id, target -> navController.navigateToBlockEditor(id, target) },
                viewModelProvider = blockViewModel,
            )
            blockWizardScreen(
                onClose = { navController.closeBlockWizard() },
                onDescribeWithAi = { target -> navController.replaceWizardWithDescribe(target) },
                onFinish = { deviceId, message -> navController.finishBlockWizard(deviceId, message) },
                viewModelProvider = wizardViewModel,
            )
            blockDescribeScreen(
                onBack = { navController.closeBlockDescribe() },
                onOpenWizard = { target -> navController.openWizardFromDescribe(target) },
                viewModelProvider = describeViewModel,
            )
            devicesScreen(
                onLinkPhone = { navController.navigateToLinkAdd() },
                onOpenDevice = { navController.navigateToRemoteDevice(it) },
                viewModelProvider = devicesViewModel,
            )
            insightsScreen(insightsContent)
            accountScreen(
                onBack = { navController.popBackStack() },
                onSignIn = { navController.navigateToSignIn() },
                onDeleteFinish = { message ->
                    showToast(message)
                    // Everything is gone: back to Welcome with no old screens underneath.
                    navController.navigate(OnboardingGraph) { popUpTo(navController.graph.id) { inclusive = true } }
                },
                viewModelProvider = accountViewModel,
            )
            linkAddScreen(
                onBack = { navController.popBackStack() },
                onScan = { navController.navigateToLinkScan() },
                onSignIn = { navController.navigateToSignIn() },
                viewModelProvider = linkAddViewModel,
            )
            linkScanScreen(
                onBack = { navController.popBackStack() },
                // The scanner is replaced by the confirm step, so Back from there returns to "Link a child's phone".
                onScan = {
                    navController.navigateToLinkConfirm(
                        it,
                        navOptions { popUpTo<LinkScan> { inclusive = true } },
                    )
                },
                onMessage = showToast,
            )
            linkConfirmScreen(
                onBack = { navController.popBackStack() },
                onLinked = { _, message ->
                    showToast(message)
                    // The handshake is over: land on Family with no link steps underneath.
                    navController.navigateToTab(MainTab.DEVICES)
                    navController.popBackStack(Devices, inclusive = false)
                },
                viewModelProvider = linkConfirmViewModel,
            )
            remoteDeviceScreen(
                onBack = { navController.popBackStack() },
                onNewBlock = { navController.navigateToBlockWizard(target = it) },
                onMessage = showToast,
                viewModelProvider = remoteDeviceViewModel,
            )
        }
    }
}
