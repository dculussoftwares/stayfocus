package com.dculus.stayfocused.kids

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.dculus.stayfocused.core.ui.components.BatteryGuidanceSheet
import com.dculus.stayfocused.core.ui.components.ToastController
import com.dculus.stayfocused.core.ui.theme.sfScreenEnter
import com.dculus.stayfocused.core.ui.theme.sfScreenExit
import com.dculus.stayfocused.core.ui.v5.components.PillButton
import com.dculus.stayfocused.core.ui.v5.components.PillButtonSize
import com.dculus.stayfocused.core.ui.v5.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.v5.components.V5ToastHost
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import com.dculus.stayfocused.feature.onboarding.PermissionsCopy
import com.dculus.stayfocused.feature.onboarding.PermissionsRoute
import com.dculus.stayfocused.feature.onboarding.PermissionsViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The Kids app UI: Permissions (first run) → Link (QR) → Consent → Home. Unlinking (or being unlinked) returns to Link.
 */
internal const val KIDS_BATTERY_GUIDANCE_TAG = "kids_battery_guidance"

@Composable
fun KidsNavigation(
    startRoute: Any,
    modifier: Modifier = Modifier,
    onSetupComplete: suspend () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    permissionsViewModel: (@Composable () -> PermissionsViewModel)? = null,
) {
    val density = LocalDensity.current
    val enter = remember(density) { sfScreenEnter(density) }
    val exit = remember { sfScreenExit() }
    val resources = LocalResources.current
    val toastScope = rememberCoroutineScope()
    val toast = remember { ToastController(toastScope) }
    // Every Kids screen is v5 and draws edge to edge.
    Box(modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startRoute,
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { enter },
            popExitTransition = { exit },
        ) {
            composable<KidsPermissions> {
                val scope = rememberCoroutineScope()
                KidsPermissionsRoute(
                    onFinish = { incomplete ->
                        scope.launch {
                            // Setup only counts as done with all four on, so the next launch asks again otherwise.
                            // The flag is stored before leaving, so a restart right after cannot show this again.
                            if (!incomplete) onSetupComplete()
                            navController.navigate(
                                KidsLink,
                                navOptions { popUpTo<KidsPermissions> { inclusive = true } },
                            )
                        }
                    },
                    viewModel = permissionsViewModel,
                )
            }
            composable<KidsLink> {
                KidsLinkRoute(
                    onClaim = { token ->
                        navController.navigate(
                            KidsConsent(token),
                            navOptions { popUpTo<KidsLink> { inclusive = true } },
                        )
                    },
                )
            }
            composable<KidsConsent> {
                KidsConsentRoute(
                    onLink = { parent ->
                        toast.show(resources.getString(R.string.kids_linked_toast, parent))
                        navController.navigate(KidsHome, navOptions { popUpTo<KidsConsent> { inclusive = true } })
                    },
                    // Declined, or the code ran out: a fresh QR code, with no Consent screen left underneath.
                    onShowNewCode = {
                        navController.navigate(KidsLink, navOptions { popUpTo<KidsConsent> { inclusive = true } })
                    },
                )
            }
            composable<KidsHome> {
                KidsHomeRoute(
                    onDone = { outcome, parent ->
                        // Unlinking by the child says so; a parent-side unlink just returns to the Link screen.
                        if (outcome == HomeOutcome.Unlinked) {
                            toast.show(resources.getString(R.string.kids_unlinked_toast, parent))
                        }
                        navController.navigate(KidsLink, navOptions { popUpTo<KidsHome> { inclusive = true } })
                    },
                )
            }
        }
        V5ToastHost(toast, bottomOffset = 24.dp)
    }
}

/** The shared system check with Kids words and the battery guidance (strongly recommended on a child's phone). */
@Composable
private fun KidsPermissionsRoute(
    onFinish: (incomplete: Boolean) -> Unit,
    viewModel: (@Composable () -> PermissionsViewModel)?,
) {
    val context = LocalContext.current
    val copy =
        remember {
            PermissionsCopy(
                body = R.string.kids_perms_body,
                disclosureTitle = R.string.kids_perms_disclosure_title,
                disclosureReads = R.string.kids_perms_disclosure_reads,
                disclosureWhy = R.string.kids_perms_disclosure_why,
                disclosureNever = R.string.kids_perms_disclosure_never,
            )
        }
    val appName = stringResource(R.string.app_name)
    val footer: @Composable () -> Unit = {
        var batteryVisible by rememberSaveable { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.kids_battery_strong),
                style = V5Theme.type.label,
                color = V5Theme.colors.ink,
            )
            PillButton(
                text = stringResource(com.dculus.stayfocused.core.ui.R.string.sf_battery_title, appName),
                onClick = { batteryVisible = true },
                variant = PillButtonVariant.White,
                size = PillButtonSize.Medium,
                modifier = Modifier.testTag(KIDS_BATTERY_GUIDANCE_TAG),
            )
            BatteryGuidanceSheet(
                visible = batteryVisible,
                onDismiss = { batteryVisible = false },
                onOpenBatterySettings = { context.openSafely(PermissionIntents.batteryOptimizationSettings()) },
                onOpenGuide = { url -> context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                appName = appName,
            )
        }
    }
    // PermissionsRoute reports "fewer than four" with a message for the phone app's Account screen; Kids has none.
    if (viewModel != null) {
        PermissionsRoute(onFinish = { _, allGranted ->
            onFinish(!allGranted)
        }, copy = copy, footer = footer, viewModel = viewModel())
    } else {
        PermissionsRoute(onFinish = { _, allGranted -> onFinish(!allGranted) }, copy = copy, footer = footer)
    }
}

private fun Context.openSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No activity for %s", intent.action)
    }
}
