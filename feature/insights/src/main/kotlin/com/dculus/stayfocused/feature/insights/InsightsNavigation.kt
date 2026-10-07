package com.dculus.stayfocused.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.dculus.stayfocused.core.navigation.Insights
import com.dculus.stayfocused.core.ui.components.NavPlaceholderScreen

fun NavController.navigateToInsights(navOptions: NavOptions? = null) = navigate(Insights, navOptions)

/** Insights tab. */
fun NavGraphBuilder.insightsScreen() {
    composable<Insights> { InsightsRoute() }
}

@Composable
internal fun InsightsRoute() {
    NavPlaceholderScreen(title = stringResource(R.string.insights_title))
}
