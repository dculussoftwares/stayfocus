package com.dculus.stayfocused.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.dculus.stayfocused.core.navigation.Home
import com.dculus.stayfocused.core.ui.components.NavPlaceholderScreen
import com.dculus.stayfocused.core.ui.components.PlaceholderAction

fun NavController.navigateToHome(navOptions: NavOptions? = null) = navigate(Home, navOptions)

/** Home tab. [onOpenAccount] is the avatar tap. */
fun NavGraphBuilder.homeScreen(onOpenAccount: () -> Unit) {
    composable<Home> { HomeRoute(onOpenAccount) }
}

@Composable
internal fun HomeRoute(onOpenAccount: () -> Unit) {
    val openAccount = stringResource(R.string.home_placeholder_open_account)
    val actions = remember(openAccount, onOpenAccount) { listOf(PlaceholderAction(openAccount, onOpenAccount)) }
    NavPlaceholderScreen(title = stringResource(R.string.home_title), actions = actions)
}
