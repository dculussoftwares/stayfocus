package com.dculus.stayfocused.feature.insights

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.dculus.stayfocused.core.navigation.Insights

fun NavController.navigateToInsights(navOptions: NavOptions? = null) = navigate(Insights, navOptions)

/** Insights tab. */
fun NavGraphBuilder.insightsScreen() {
    composable<Insights> { InsightsRoute() }
}
