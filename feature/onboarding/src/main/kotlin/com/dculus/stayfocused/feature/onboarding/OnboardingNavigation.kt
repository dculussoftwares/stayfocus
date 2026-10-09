package com.dculus.stayfocused.feature.onboarding

import androidx.compose.runtime.Composable
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

fun NavController.navigateToOnboarding(navOptions: NavOptions? = null) = navigate(OnboardingGraph, navOptions)

fun NavController.navigateToSignIn(navOptions: NavOptions? = null) = navigate(SignIn, navOptions)

fun NavController.navigateToPermissions(navOptions: NavOptions? = null) = navigate(Permissions, navOptions)

/**
 * The onboarding graph: Welcome, SignIn, Permissions. [onFinished] runs after the last step with the toast to show
 * on the next screen (null when all four permissions are on). The view-model parameters are null to use Hilt
 * (navigation tests run without it).
 */
fun NavGraphBuilder.onboardingGraph(
    navController: NavController,
    onFinished: (toast: String?) -> Unit,
    signInViewModel: (@Composable () -> SignInViewModel)? = null,
    permissionsViewModel: (@Composable () -> PermissionsViewModel)? = null,
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
        composable<Permissions> {
            PermissionsRoute(onFinish = onFinished, viewModel = permissionsViewModel?.invoke() ?: hiltViewModel())
        }
    }
}
