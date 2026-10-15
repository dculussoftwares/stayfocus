@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.account

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.blocking.permissions.PermissionIntents
import com.dculus.stayfocused.core.model.Permissions
import com.dculus.stayfocused.core.sync.AuthUser
import com.dculus.stayfocused.core.ui.components.BatteryGuidance
import com.dculus.stayfocused.core.ui.components.GhostButton
import com.dculus.stayfocused.core.ui.components.LedDot
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.components.OutlineButton
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.components.SfBottomSheet
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import timber.log.Timber

internal const val ACCOUNT_SIGN_IN_TAG = "account_sign_in"
internal const val ACCOUNT_SIGN_OUT_TAG = "account_sign_out"
internal const val ACCOUNT_DELETE_TAG = "account_delete"
internal const val ACCOUNT_BACK_TAG = "account_back"
internal const val ACCOUNT_OSS_TAG = "account_oss"
internal const val ACCOUNT_DISCLOSURE_AGREE_TAG = "account_disclosure_agree"

internal const val GITHUB_URL = "https://github.com/dculussoftwares/stayfocus"
internal const val LICENSE_URL = "https://github.com/dculussoftwares/stayfocus/blob/main/LICENSE"
internal const val PRIVACY_URL = "https://dculussoftwares.github.io/stayfocus/privacy.html"

private val OffLed = Color(0xFF3A3E35)

/** The rows of the health list, in display order. */
internal enum class HealthRow(
    val tag: String,
) {
    Usage("usage"),
    Accessibility("accessibility"),
    Overlay("overlay"),
    Notifications("notifications"),
    Battery("battery"),
    Engine("engine"),
}

internal fun healthTag(row: HealthRow) = "account_health_${row.tag}"

internal fun HealthRow.isOn(state: AccountUiState): Boolean =
    when (this) {
        HealthRow.Usage -> state.permissions.usage
        HealthRow.Accessibility -> state.permissions.accessibility
        HealthRow.Overlay -> state.permissions.overlay
        HealthRow.Notifications -> state.permissions.notifications
        HealthRow.Battery -> state.permissions.batteryUnrestricted
        HealthRow.Engine -> state.engine.serviceBound
    }

@Composable
internal fun AccountRoute(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onDeleteFinish: (message: String) -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleteState by viewModel.delete.collectAsStateWithLifecycle()
    val deletedMessage = stringResource(R.string.account_deleted_toast)
    val currentOnDeleteFinish by rememberUpdatedState(onDeleteFinish)
    LaunchedEffect(deleteState.deleted) {
        if (deleteState.deleted) {
            viewModel.deleteHandled()
            currentOnDeleteFinish(deletedMessage)
        }
    }
    val context = LocalContext.current
    var disclosureVisible by rememberSaveable { mutableStateOf(false) }
    var batteryVisible by rememberSaveable { mutableStateOf(false) }
    var licencesVisible by rememberSaveable { mutableStateOf(false) }
    val version = remember(context) { context.versionName() }
    AccountScreen(
        state = state,
        version = version,
        onBack = onBack,
        onSignIn = onSignIn,
        onFix = { row ->
            when (row) {
                HealthRow.Usage -> context.open(PermissionIntents.usageAccess())

                HealthRow.Overlay -> context.open(PermissionIntents.overlay(context.packageName))

                // Never straight to Settings: the disclosure comes first.
                HealthRow.Accessibility -> disclosureVisible = true

                HealthRow.Notifications -> context.open(PermissionIntents.notificationSettings(context.packageName))

                HealthRow.Battery -> batteryVisible = true

                // Stopped means Android unbound the service: turning it on in Accessibility settings restarts it.
                HealthRow.Engine -> disclosureVisible = true
            }
        },
        onOpenUrl = { context.open(Intent(Intent.ACTION_VIEW, Uri.parse(it))) },
        onLicences = { licencesVisible = true },
        onSignOut = viewModel::signOut,
        onDeleteAccount = viewModel::startDelete,
    )
    DeleteAccountSheet(
        state = deleteState,
        onConfirm = viewModel::confirmDelete,
        onCancel = viewModel::cancelDelete,
        onPassword = viewModel::reauthenticateWithPassword,
        onGoogle = { viewModel.reauthenticateWithGoogle(context) },
    )
    SfBottomSheet(visible = disclosureVisible, onDismiss = { disclosureVisible = false }) {
        // PROVISIONAL: M5-05 replaces this with the shared full disclosure.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.account_disclosure_title),
                style = StayFocusedTheme.type.displayM,
                color = StayFocusedTheme.colors.text,
            )
            Text(
                stringResource(R.string.account_disclosure_body),
                style = StayFocusedTheme.type.body,
                color = StayFocusedTheme.colors.secondary,
            )
            PrimaryButton(
                text = stringResource(R.string.account_disclosure_agree),
                onClick = {
                    disclosureVisible = false
                    context.open(PermissionIntents.accessibility())
                },
                modifier = Modifier.testTag(ACCOUNT_DISCLOSURE_AGREE_TAG),
            )
            GhostButton(
                text = stringResource(R.string.account_disclosure_no),
                onClick = { disclosureVisible = false },
            )
        }
    }
    SfBottomSheet(visible = batteryVisible, onDismiss = { batteryVisible = false }) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            BatteryGuidance(
                onOpenBatterySettings = { context.open(PermissionIntents.batteryOptimizationSettings()) },
                onOpenGuide = { context.open(Intent(Intent.ACTION_VIEW, Uri.parse(it))) },
            )
        }
    }
    LicencesSheet(visible = licencesVisible, onDismiss = { licencesVisible = false })
}

private fun Context.versionName(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No activity for %s", intent.action)
    }
}

@Composable
internal fun AccountScreen(
    state: AccountUiState,
    version: String,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onFix: (HealthRow) -> Unit,
    onOpenUrl: (String) -> Unit,
    onLicences: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val back = stringResource(R.string.account_back)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .testTag(ACCOUNT_BACK_TAG)
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.panel)
                    .border(1.dp, colors.hairline10, RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = back, role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = back },
                contentAlignment = Alignment.Center,
            ) {
                Text("←", color = colors.text, style = type.bodyL)
            }
            Text(stringResource(R.string.account_title), style = type.displayM, color = colors.text)
        }
        if (state.authLoaded) ProfileHeader(state.user, onSignIn)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MonoLabel(stringResource(R.string.account_system_check), Modifier.padding(horizontal = 4.dp))
            Column(
                Modifier.clip(RoundedCornerShape(20.dp)).background(colors.hairline06),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                HealthRow.entries.forEach { row -> HealthItem(row, state, onFix) }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.panel)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.account_oss_blurb), style = type.body, color = colors.secondary)
            LinkRow(stringResource(R.string.account_link_github)) { onOpenUrl(GITHUB_URL) }
            LinkRow(stringResource(R.string.account_link_license)) { onOpenUrl(LICENSE_URL) }
            LinkRow(stringResource(R.string.account_link_oss), ACCOUNT_OSS_TAG, onLicences)
            LinkRow(stringResource(R.string.account_link_privacy)) { onOpenUrl(PRIVACY_URL) }
            if (version.isNotEmpty()) {
                Text(
                    stringResource(R.string.account_version, version),
                    style = type.label.copy(fontSize = 11.sp),
                    color = colors.tertiary,
                )
            }
        }
        if (state.user != null) {
            OutlineButton(
                text = stringResource(R.string.account_sign_out),
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().testTag(ACCOUNT_SIGN_OUT_TAG),
            )
            GhostButton(
                text = stringResource(R.string.account_delete),
                onClick = onDeleteAccount,
                modifier = Modifier.fillMaxWidth().testTag(ACCOUNT_DELETE_TAG),
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    user: AuthUser?,
    onSignIn: () -> Unit,
) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(
                    if (user !=
                        null
                    ) {
                        colors.accent
                    } else {
                        colors.track
                    },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    user?.initial() ?: "?",
                    style = type.displayM.copy(fontSize = 26.sp),
                    color = if (user != null) colors.onAccent else colors.secondary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (user != null) {
                    Text(user.shownName() ?: "", style = type.displayM.copy(fontSize = 24.sp), color = colors.text)
                    user.email?.let { Text(it, style = type.label.copy(fontSize = 12.sp), color = colors.secondary) }
                } else {
                    Text(
                        stringResource(R.string.account_not_signed_in),
                        style = type.displayM.copy(fontSize = 24.sp),
                        color = colors.text,
                    )
                    Text(
                        stringResource(R.string.account_not_signed_in_hint),
                        style = type.bodyS,
                        color = colors.secondary,
                    )
                }
            }
        }
        if (user == null) {
            PrimaryButton(
                text = stringResource(R.string.account_sign_in),
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().testTag(ACCOUNT_SIGN_IN_TAG),
            )
        }
    }
}

@Composable
private fun HealthItem(
    row: HealthRow,
    state: AccountUiState,
    onFix: (HealthRow) -> Unit,
) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    val on = row.isOn(state)
    val title =
        stringResource(
            when (row) {
                HealthRow.Usage -> R.string.account_perm_usage
                HealthRow.Accessibility -> R.string.account_perm_accessibility
                HealthRow.Overlay -> R.string.account_perm_overlay
                HealthRow.Notifications -> R.string.account_perm_notifications
                HealthRow.Battery -> R.string.account_perm_battery
                HealthRow.Engine -> R.string.account_perm_engine
            },
        )
    val cta =
        stringResource(
            when {
                row == HealthRow.Engine -> if (on) R.string.account_engine_running else R.string.account_engine_stopped
                on -> R.string.account_cta_on
                row == HealthRow.Battery -> R.string.account_cta_fix
                else -> R.string.account_cta_allow
            },
        )
    // The engine row only explains (and offers a fix) when the service is off; a running engine is inert.
    val actionable = !on
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.panel)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LedDot(color = if (on) colors.accent else OffLed, size = 8.dp, glow = on)
        Text(
            title,
            Modifier.weight(1f),
            style = type.bodyL.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = colors.text,
        )
        Box(
            Modifier
                .testTag(healthTag(row))
                .clip(RoundedCornerShape(8.dp))
                .background(if (on) colors.accent.copy(alpha = 0.14f) else colors.text)
                .clickable(enabled = actionable, role = Role.Button) { onFix(row) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                cta.uppercase(),
                style = type.label.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                color = if (on) colors.accent else colors.onAccent,
            )
        }
    }
}

@Composable
private fun LinkRow(
    text: String,
    tag: String? = null,
    onClick: () -> Unit,
) {
    Text(
        text,
        Modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        style = StayFocusedTheme.type.bodyL.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
        color = StayFocusedTheme.colors.text,
    )
}

@Composable
internal fun LicencesSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    SfBottomSheet(visible = visible, onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.account_oss_title),
                style = StayFocusedTheme.type.displayM,
                color = StayFocusedTheme.colors.text,
            )
            Text(
                stringResource(R.string.account_oss_body),
                style = StayFocusedTheme.type.body,
                color = StayFocusedTheme.colors.secondary,
            )
            Text(
                stringResource(R.string.account_oss_list),
                style = StayFocusedTheme.type.body,
                color = StayFocusedTheme.colors.text,
            )
            OutlineButton(text = stringResource(R.string.account_oss_close), onClick = onDismiss)
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
internal fun AccountScreenPreview() {
    StayFocusedTheme {
        AccountScreen(
            state =
                AccountUiState(
                    user = AuthUser("u", "alex@example.com", "Alex"),
                    permissions =
                        Permissions(
                            usage = true,
                            accessibility = false,
                            overlay = true,
                            notifications = true,
                        ),
                ),
            version = "1.0.0",
            onBack = {},
            onSignIn = {},
            onFix = {},
            onOpenUrl = {},
            onLicences = {},
            onSignOut = {},
            onDeleteAccount = {},
        )
    }
}
