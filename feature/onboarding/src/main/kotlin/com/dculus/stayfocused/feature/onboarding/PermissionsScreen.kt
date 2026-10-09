@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.blocking.permissions.PermissionIntents
import com.dculus.stayfocused.core.model.Permissions
import com.dculus.stayfocused.core.ui.components.GhostButton
import com.dculus.stayfocused.core.ui.components.LedDot
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.components.OutlineButton
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.components.SfBottomSheet
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import timber.log.Timber

internal const val PERMS_CONTINUE_TAG = "perms_continue"
internal const val PERMS_DISCLOSURE_AGREE_TAG = "perms_disclosure_agree"
internal const val PERMS_DISCLOSURE_NO_TAG = "perms_disclosure_no"

internal fun permsAllowTag(row: PermissionRow) = "perms_allow_${row.tag}"

private val OffLed = Color(0xFF3A3E35)

/**
 * The system check. [onFinish] gets the "Blocking needs all 4" message when leaving with fewer than four
 * permissions (the host shows it on the next screen), or null when all four are on.
 */
@Composable
internal fun PermissionsRoute(
    onFinish: (message: String?) -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
    disclosure: @Composable (visible: Boolean, onAgree: () -> Unit, onDismiss: () -> Unit) -> Unit =
        { visible, onAgree, onDismiss -> ProvisionalAccessibilityDisclosure(visible, onAgree, onDismiss) },
) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var disclosureVisible by rememberSaveable { mutableStateOf(false) }
    val notificationRuntime = PermissionIntents.notificationRuntimePermission()
    // The result needs no handling: the repository re-reads when the app resumes after the dialog closes.
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val incomplete = stringResource(R.string.perms_toast_incomplete)
    PermissionsScreen(
        permissions = permissions,
        onAllow = { row ->
            when (row) {
                PermissionRow.Usage -> {
                    context.open(PermissionIntents.usageAccess())
                }

                PermissionRow.Overlay -> {
                    context.open(PermissionIntents.overlay(context.packageName))
                }

                // Never straight to Settings: the disclosure comes first.
                PermissionRow.Accessibility -> {
                    disclosureVisible = true
                }

                PermissionRow.Notifications -> {
                    when (viewModel.nextNotificationStep(notificationRuntime != null)) {
                        NotificationStep.RequestRuntime -> {
                            notificationRuntime?.let(requestNotifications::launch)
                        }

                        NotificationStep.OpenSettings -> {
                            context.open(PermissionIntents.notificationSettings(context.packageName))
                        }
                    }
                }
            }
        },
        onContinue = { onFinish(if (permissions.grantedCount() < PermissionRow.entries.size) incomplete else null) },
    )
    disclosure(
        disclosureVisible,
        {
            disclosureVisible = false
            context.open(PermissionIntents.accessibility())
        },
        { disclosureVisible = false },
    )
}

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No activity for %s", intent.action)
    }
}

@Composable
internal fun PermissionsScreen(
    permissions: Permissions,
    onAllow: (PermissionRow) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    val granted = permissions.grantedCount()
    val total = PermissionRow.entries.size
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MonoLabel(stringResource(R.string.perms_system_check), color = colors.secondary)
                    Text(
                        stringResource(R.string.perms_count, granted),
                        style = type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = colors.accent,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PermissionRow.entries.forEach { row ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (row.isGranted(permissions)) colors.accent else colors.track),
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.perms_headline_1) + "\n" + stringResource(R.string.perms_headline_2),
                    style = type.displayL.copy(fontSize = 32.sp, lineHeight = 33.sp, letterSpacing = (-0.6).sp),
                    color = colors.text,
                )
                Text(stringResource(R.string.perms_body), style = type.body, color = colors.secondary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PermissionRow.entries.forEach { row ->
                    PermissionCard(row, row.isGranted(permissions)) { onAllow(row) }
                }
            }
        }
        if (granted == total) {
            PrimaryButton(
                text = stringResource(R.string.perms_continue_all),
                onClick = onContinue,
                modifier = Modifier.testTag(PERMS_CONTINUE_TAG),
            )
        } else {
            OutlineButton(
                text = stringResource(R.string.perms_continue_now),
                onClick = onContinue,
                modifier = Modifier.testTag(PERMS_CONTINUE_TAG),
            )
        }
    }
}

@Composable
private fun PermissionCard(
    row: PermissionRow,
    on: Boolean,
    onAllow: () -> Unit,
) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.panel)
            .border(1.dp, if (on) colors.accent.copy(alpha = 0.3f) else colors.hairline06, shape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LedDot(color = if (on) colors.accent else OffLed, size = 10.dp, glow = on)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                stringResource(row.title),
                style = type.bodyL.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = colors.text,
            )
            Text(
                stringResource(row.why),
                style = type.bodyS.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                color = colors.secondary,
            )
        }
        val buttonShape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .testTag(permsAllowTag(row))
                .height(36.dp)
                .clip(buttonShape)
                .background(if (on) colors.accent.copy(alpha = 0.14f) else colors.text)
                .clickable(enabled = !on, role = Role.Button, onClick = onAllow)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (on) R.string.perms_on else R.string.perms_allow),
                style = type.bodyL.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
                color = if (on) colors.accent else colors.onAccent,
            )
        }
    }
}

/** PROVISIONAL: M5-05 replaces this with the full disclosure (what it reads, why, never). */
@Composable
internal fun ProvisionalAccessibilityDisclosure(
    visible: Boolean,
    onAgree: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = StayFocusedTheme.colors
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
                stringResource(R.string.perms_disclosure_title),
                style = StayFocusedTheme.type.displayM,
                color = colors.text,
            )
            Text(
                stringResource(R.string.perms_disclosure_body),
                style = StayFocusedTheme.type.body,
                color = colors.secondary,
            )
            PrimaryButton(
                text = stringResource(R.string.perms_disclosure_agree),
                onClick = onAgree,
                modifier = Modifier.testTag(PERMS_DISCLOSURE_AGREE_TAG),
            )
            GhostButton(
                text = stringResource(R.string.perms_disclosure_no),
                onClick = onDismiss,
                modifier = Modifier.testTag(PERMS_DISCLOSURE_NO_TAG),
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
internal fun PermissionsScreenPreview() {
    StayFocusedTheme {
        PermissionsScreen(
            Permissions(usage = true, accessibility = false, overlay = true, notifications = false),
            {},
            {},
        )
    }
}
