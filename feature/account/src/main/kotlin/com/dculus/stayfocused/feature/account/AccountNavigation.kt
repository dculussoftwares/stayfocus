package com.dculus.stayfocused.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.dculus.stayfocused.core.navigation.Account
import com.dculus.stayfocused.core.ui.components.NavPlaceholderScreen

fun NavController.navigateToAccount(navOptions: NavOptions? = null) = navigate(Account, navOptions)

/** Account, opened from the avatar on Home. */
fun NavGraphBuilder.accountScreen() {
    composable<Account> { AccountRoute() }
}

@Composable
internal fun AccountRoute() {
    NavPlaceholderScreen(title = stringResource(R.string.account_title))
}
