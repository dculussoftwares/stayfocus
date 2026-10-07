package com.dculus.stayfocused.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.dculus.stayfocused.R
import com.dculus.stayfocused.core.navigation.Account
import com.dculus.stayfocused.core.navigation.Block
import com.dculus.stayfocused.core.navigation.Devices
import com.dculus.stayfocused.core.navigation.Home
import com.dculus.stayfocused.core.navigation.Insights
import com.dculus.stayfocused.core.navigation.RemoteDevice
import com.dculus.stayfocused.core.ui.icon.TabIcons

/** The four tabs, in tab-bar order. [route] is the tab's root destination. */
enum class MainTab(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    HOME(Home, R.string.tab_home, TabIcons.Home),
    BLOCK(Block, R.string.tab_block, TabIcons.Block),
    DEVICES(Devices, R.string.tab_devices, TabIcons.Devices),
    INSIGHTS(Insights, R.string.tab_insights, TabIcons.Insights),
}

/** Destinations that show the tab bar (prototype `showNav`): the tab roots, Remote device and Account. */
private val tabBarRoutes =
    listOf(Home::class, Block::class, Devices::class, Insights::class, RemoteDevice::class, Account::class)

internal fun NavDestination?.showsTabBar(): Boolean = this != null && tabBarRoutes.any { hasRoute(it) }

/** The highlighted tab: a tab root or its Remote device sub-screen. Account highlights none. */
internal fun NavDestination?.selectedTab(): MainTab? =
    when {
        this == null -> null
        hasRoute(Home::class) -> MainTab.HOME
        hasRoute(Block::class) -> MainTab.BLOCK
        hasRoute(Devices::class) || hasRoute(RemoteDevice::class) -> MainTab.DEVICES
        hasRoute(Insights::class) -> MainTab.INSIGHTS
        else -> null
    }

/**
 * Switches to [tab]. Reselecting the current tab pops to its root; otherwise the tab's back stack is saved and
 * restored, and Home stays at the bottom so back from any tab lands on Home (and back from Home exits).
 */
internal fun NavController.navigateToTab(tab: MainTab) {
    if (currentDestination.selectedTab() == tab) {
        popBackStack(tab.route, inclusive = false)
        return
    }
    navigate(tab.route) {
        popUpTo(Home) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
