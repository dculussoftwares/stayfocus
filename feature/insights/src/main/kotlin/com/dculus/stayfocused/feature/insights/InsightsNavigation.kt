package com.dculus.stayfocused.feature.insights

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.dculus.stayfocused.core.navigation.Insights

fun NavController.navigateToInsights(navOptions: NavOptions? = null) = navigate(Insights, navOptions)

/** Insights tab. [content] replaces the real screen, for tests that run without Hilt. */
fun NavGraphBuilder.insightsScreen(content: (@Composable () -> Unit)? = null) {
    composable<Insights> { if (content != null) content() else InsightsRoute() }
}
