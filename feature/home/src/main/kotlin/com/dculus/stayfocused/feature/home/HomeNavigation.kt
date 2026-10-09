package com.dculus.stayfocused.feature.home

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.dculus.stayfocused.core.navigation.Home

fun NavController.navigateToHome(navOptions: NavOptions? = null) = navigate(Home, navOptions)

/**
 * Home tab. [destinations] says where its buttons lead. [viewModelProvider] supplies the ViewModel; null uses Hilt
 * (lets navigation tests run without a Hilt application).
 */
fun NavGraphBuilder.homeScreen(
    destinations: HomeDestinations,
    viewModelProvider: (@Composable () -> HomeViewModel)? = null,
) {
    composable<Home> { HomeRoute(destinations, viewModelProvider?.invoke() ?: hiltViewModel()) }
}
