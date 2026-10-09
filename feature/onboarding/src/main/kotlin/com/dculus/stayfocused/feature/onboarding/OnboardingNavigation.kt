package com.dculus.stayfocused.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.dculus.stayfocused.core.navigation.OnboardingGraph
import com.dculus.stayfocused.core.navigation.Permissions
import com.dculus.stayfocused.core.navigation.SignIn
import com.dculus.stayfocused.core.navigation.Welcome
import com.dculus.stayfocused.core.ui.components.NavPlaceholderScreen
import com.dculus.stayfocused.core.ui.components.PlaceholderAction

fun NavController.navigateToOnboarding(navOptions: NavOptions? = null) = navigate(OnboardingGraph, navOptions)

fun NavController.navigateToSignIn(navOptions: NavOptions? = null) = navigate(SignIn, navOptions)

fun NavController.navigateToPermissions(navOptions: NavOptions? = null) = navigate(Permissions, navOptions)

/**
 * The onboarding graph: Welcome, SignIn, Permissions. [onFinished] runs after the last step. [signInViewModel] null
 * uses Hilt (navigation tests run without it).
 */
fun NavGraphBuilder.onboardingGraph(
    navController: NavController,
    onFinished: () -> Unit,
    signInViewModel: (@Composable () -> SignInViewModel)? = null,
) {
    navigation<OnboardingGraph>(startDestination = Welcome) {
        composable<Welcome> { WelcomeRoute(onContinue = { navController.navigateToSignIn() }) }
        composable<SignIn> {
            SignInRoute(
                onContinue = { navController.navigateToPermissions() },
                onBack = { navController.popBackStack() },
                viewModel = signInViewModel?.invoke() ?: hiltViewModel(),
            )
        }
        composable<Permissions> { PermissionsRoute(onContinue = onFinished) }
    }
}

@Composable
internal fun PermissionsRoute(onContinue: () -> Unit) = Step(stringResource(R.string.permissions_title), onContinue)

@Composable
private fun Step(
    title: String,
    onContinue: () -> Unit,
) {
    val next = stringResource(R.string.onboarding_placeholder_continue)
    val actions = remember(next, onContinue) { listOf(PlaceholderAction(next, onContinue)) }
    NavPlaceholderScreen(title = title, actions = actions)
}
