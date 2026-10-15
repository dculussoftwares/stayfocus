package com.dculus.stayfocused.feature.account

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.dculus.stayfocused.core.navigation.Account

fun NavController.navigateToAccount(navOptions: NavOptions? = null) = navigate(Account, navOptions)

/**
 * Account, opened from the avatar on Home. [onSignIn] starts sign-in for a person who skipped it and
 * [onDeleteFinish] runs after the account and its data are deleted, with the toast to show (M6-06); the app then
 * returns to Welcome. [viewModelProvider] null uses Hilt (navigation tests run
 * without it).
 */
fun NavGraphBuilder.accountScreen(
    onBack: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onDeleteFinish: (message: String) -> Unit = {},
    viewModelProvider: (@Composable () -> AccountViewModel)? = null,
) {
    composable<Account> {
        AccountRoute(
            onBack = onBack,
            onSignIn = onSignIn,
            onDeleteFinish = onDeleteFinish,
            viewModel = viewModelProvider?.invoke() ?: hiltViewModel(),
        )
    }
}
