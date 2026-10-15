package com.dculus.stayfocused.kids

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.dculus.stayfocused.core.blocking.permissions.PermissionIntents
import com.dculus.stayfocused.core.navigation.KidsConsent
import com.dculus.stayfocused.core.navigation.KidsHome
import com.dculus.stayfocused.core.navigation.KidsLink
import com.dculus.stayfocused.core.navigation.KidsPermissions
import com.dculus.stayfocused.core.ui.components.BatteryGuidance
import com.dculus.stayfocused.core.ui.components.NavPlaceholderScreen
import com.dculus.stayfocused.core.ui.components.PlaceholderAction
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.sfScreenEnter
import com.dculus.stayfocused.core.ui.theme.sfScreenExit
import com.dculus.stayfocused.feature.onboarding.PermissionsCopy
import com.dculus.stayfocused.feature.onboarding.PermissionsRoute
import com.dculus.stayfocused.feature.onboarding.PermissionsViewModel
import timber.log.Timber

/**
 * The Kids app UI: Permissions (first run) → Link (QR) → Consent → Home. Link, Consent and Home are placeholders
 * until their own stories; they exist now so the route chain and the start logic are in place.
 */
@Composable
fun KidsNavigation(
    startRoute: Any,
    modifier: Modifier = Modifier,
    onSetupComplete: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    permissionsViewModel: (@Composable () -> PermissionsViewModel)? = null,
) {
    val density = LocalDensity.current
    val enter = remember(density) { sfScreenEnter(density) }
    val exit = remember { sfScreenExit() }
    Box(modifier.fillMaxSize().background(StayFocusedTheme.colors.background).safeDrawingPadding()) {
        NavHost(
            navController = navController,
            startDestination = startRoute,
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { enter },
            popExitTransition = { exit },
        ) {
            composable<KidsPermissions> {
                KidsPermissionsRoute(
                    onFinish = {
                        onSetupComplete()
                        navController.navigate(KidsLink, navOptions { popUpTo<KidsPermissions> { inclusive = true } })
                    },
                    viewModel = permissionsViewModel,
                )
            }
            composable<KidsLink> {
                val next = stringResource(R.string.kids_next)
                NavPlaceholderScreen(
                    title = stringResource(R.string.kids_link_title),
                    actions = listOf(PlaceholderAction(next) { navController.navigate(KidsConsent) }),
                )
            }
            composable<KidsConsent> {
                val next = stringResource(R.string.kids_next)
                NavPlaceholderScreen(
                    title = stringResource(R.string.kids_consent_title),
                    actions = listOf(PlaceholderAction(next) { navController.navigate(KidsHome) }),
                )
            }
            composable<KidsHome> {
                NavPlaceholderScreen(title = stringResource(R.string.kids_home_title))
            }
        }
    }
}

/** The shared system check with Kids words and the battery guidance (strongly recommended on a child's phone). */
@Composable
private fun KidsPermissionsRoute(
    onFinish: () -> Unit,
    viewModel: (@Composable () -> PermissionsViewModel)?,
) {
    val context = LocalContext.current
    val copy =
        remember {
            PermissionsCopy(
                body = R.string.kids_perms_body,
                disclosureWhy = R.string.kids_perms_disclosure_why,
                disclosureNever = R.string.kids_perms_disclosure_never,
            )
        }
    val appName = stringResource(R.string.app_name)
    val footer: @Composable () -> Unit = {
        BatteryGuidance(
            onOpenBatterySettings = { context.openSafely(PermissionIntents.batteryOptimizationSettings()) },
            onOpenGuide = { url -> context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            appName = appName,
        )
    }
    // The message about fewer than four permissions is for the phone app's Account screen; Kids has none.
    if (viewModel != null) {
        PermissionsRoute(onFinish = { onFinish() }, copy = copy, footer = footer, viewModel = viewModel())
    } else {
        PermissionsRoute(onFinish = { onFinish() }, copy = copy, footer = footer)
    }
}

private fun Context.openSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No activity for %s", intent.action)
    }
}
