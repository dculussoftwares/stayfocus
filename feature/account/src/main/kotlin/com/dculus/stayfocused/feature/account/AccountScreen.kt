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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.dculus.stayfocused.core.blocking.BlockingLog
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
import com.dculus.stayfocused.core.ui.v5.components.LocalTabBarPadding
import com.dculus.stayfocused.core.ui.v5.components.PillButton
import com.dculus.stayfocused.core.ui.v5.components.PillButtonSize
import com.dculus.stayfocused.core.ui.v5.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.v5.components.RoundIconButton
import com.dculus.stayfocused.core.ui.v5.components.V5BottomSheet
import com.dculus.stayfocused.core.ui.v5.components.V5Card
import com.dculus.stayfocused.core.ui.v5.icons.V5Icons
import com.dculus.stayfocused.core.ui.v5.theme.V5Screen
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import com.dculus.stayfocused.core.ui.v5.theme.v5StatusBarPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val scope = rememberCoroutineScope()
    val logEmpty = stringResource(R.string.account_share_log_empty)
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
        // TEMPORARY, debug builds only: remove with BlockingLog
        onShareLog =
            if (BlockingLog.isEnabled(context)) {
                {
                    scope.launch {
                        val log = withContext(Dispatchers.IO) { BlockingLog.tail() }
                        context.shareBlockingLog(log ?: logEmpty)
                    }
                }
            } else {
                null
            },
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

/** TEMPORARY: shares the newest blocking-debug log lines as text (no file provider needed). */
private fun Context.shareBlockingLog(log: String) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.account_share_log_subject))
            .putExtra(Intent.EXTRA_TEXT, log)
    open(Intent.createChooser(send, getString(R.string.account_share_log_chooser)))
}

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
    onShareLog: (() -> Unit)? = null,
) {
    V5Screen(darkContent = false, modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .v5StatusBarPadding(8.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, bottom = 30.dp + LocalTabBarPadding.current),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val back = stringResource(R.string.account_back)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(V5Icons.back, back, onBack, Modifier.testTag(ACCOUNT_BACK_TAG))
                Text(stringResource(R.string.account_title), style = V5Theme.type.title, color = V5Theme.colors.ink)
            }
            if (state.authLoaded) V5ProfileHeader(state.user, onSignIn)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.account_system_check),
                    Modifier.padding(horizontal = 4.dp),
                    style = V5Theme.type.label,
                    color = V5Theme.colors.textSecondary,
                )
                V5Card(Modifier.fillMaxWidth(), padding = 0.dp) {
                    HealthRow.entries.forEach { row -> V5HealthItem(row, state, onFix) }
                }
            }
            V5Card(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.account_oss_blurb),
                    style = V5Theme.type.body,
                    color = V5Theme.colors.textSecondary,
                )
                V5LinkRow(stringResource(R.string.account_link_github)) { onOpenUrl(GITHUB_URL) }
                V5LinkRow(stringResource(R.string.account_link_license)) { onOpenUrl(LICENSE_URL) }
                V5LinkRow(stringResource(R.string.account_link_oss), ACCOUNT_OSS_TAG, onLicences)
                V5LinkRow(stringResource(R.string.account_link_privacy)) { onOpenUrl(PRIVACY_URL) }
                if (version.isNotEmpty()) {
                    Text(
                        stringResource(R.string.account_version, version),
                        style = V5Theme.type.label,
                        color = V5Theme.colors.textSecondary,
                    )
                }
            }
            if (onShareLog != null) {
                PillButton(
                    stringResource(R.string.account_share_log),
                    onShareLog,
                    Modifier.fillMaxWidth(),
                    PillButtonVariant.Secondary,
                )
            }
            if (state.user != null) {
                PillButton(
                    stringResource(R.string.account_sign_out),
                    onSignOut,
                    Modifier.fillMaxWidth().testTag(ACCOUNT_SIGN_OUT_TAG),
                    PillButtonVariant.Secondary,
                )
                PillButton(
                    stringResource(R.string.account_delete),
                    onDeleteAccount,
                    Modifier.fillMaxWidth().testTag(ACCOUNT_DELETE_TAG),
                    PillButtonVariant.Text,
                    PillButtonSize.Medium,
                    textColor = V5Theme.colors.destructive,
                )
            }
        }
    }
}

@Composable
private fun V5ProfileHeader(
    user: AuthUser?,
    onSignIn: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(26.dp)).background(
                    if (user != null) V5Theme.colors.ink else V5Theme.colors.track,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    user?.initial() ?: "?",
                    style = V5Theme.type.heading,
                    color = if (user != null) V5Theme.colors.onGradient else V5Theme.colors.textSecondary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (user != null) {
                    Text(user.shownName() ?: "", style = V5Theme.type.cardTitle, color = V5Theme.colors.ink)
                    user.email?.let { Text(it, style = V5Theme.type.body, color = V5Theme.colors.textSecondary) }
                } else {
                    Text(
                        stringResource(R.string.account_not_signed_in),
                        style = V5Theme.type.cardTitle,
                        color = V5Theme.colors.ink,
                    )
                    Text(
                        stringResource(R.string.account_not_signed_in_hint),
                        style = V5Theme.type.body,
                        color = V5Theme.colors.textSecondary,
                    )
                }
            }
        }
        if (user == null) {
            PillButton(
                stringResource(R.string.account_sign_in),
                onSignIn,
                Modifier.fillMaxWidth().testTag(ACCOUNT_SIGN_IN_TAG),
            )
        }
    }
}

@Composable
private fun V5HealthItem(
    row: HealthRow,
    state: AccountUiState,
    onFix: (HealthRow) -> Unit,
) {
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LedDot(color = if (on) V5Theme.colors.success else V5Theme.colors.hairline, size = 8.dp, glow = on)
        Text(
            title,
            Modifier.weight(1f),
            style = V5Theme.type.bodyL.copy(fontSize = 15.sp),
            color = V5Theme.colors.ink,
        )
        Box(
            Modifier
                .testTag(healthTag(row))
                .clip(RoundedCornerShape(22.dp))
                .background(if (on) V5Theme.colors.onlineBackground else V5Theme.colors.ink)
                .clickable(enabled = actionable, role = Role.Button) { onFix(row) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                cta.uppercase(),
                style = V5Theme.type.label.copy(fontSize = 12.sp),
                color = if (on) V5Theme.colors.onlineText else V5Theme.colors.onGradient,
            )
        }
    }
}

@Composable
private fun V5LinkRow(
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
            .padding(top = 10.dp),
        style = V5Theme.type.bodyL.copy(fontSize = 15.sp),
        color = V5Theme.colors.ink,
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
