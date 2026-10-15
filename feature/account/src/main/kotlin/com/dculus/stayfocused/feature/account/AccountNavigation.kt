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
 * [onDeleteAccount] opens the delete flow (M6-06). [viewModelProvider] null uses Hilt (navigation tests run
 * without it).
 */
fun NavGraphBuilder.accountScreen(
    onBack: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    viewModelProvider: (@Composable () -> AccountViewModel)? = null,
) {
    composable<Account> {
        AccountRoute(
            onBack = onBack,
            onSignIn = onSignIn,
            onDeleteAccount = onDeleteAccount,
            viewModel = viewModelProvider?.invoke() ?: hiltViewModel(),
        )
    }
}
